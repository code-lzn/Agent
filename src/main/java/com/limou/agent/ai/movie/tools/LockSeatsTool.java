package com.limou.agent.ai.movie.tools;

import cn.hutool.json.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limou.agent.ai.tools.BaseTool;
import com.limou.agent.mapper.SeatMapper;
import com.limou.agent.model.entity.Seat;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 座位锁定工具
 * 使用数据库乐观锁 + Redis 分布式锁防止超卖
 */
@Slf4j
@Component
public class LockSeatsTool extends BaseTool {

    @Resource
    private SeatMapper seatMapper;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private ObjectMapper objectMapper;

    @Tool(description = "锁定指定场次的座位。传入场次ID和座位ID数组。返回锁定结果JSON，成功则包含已锁座位信息和总价，失败则包含冲突座位和推荐替代")
    @Transactional(rollbackFor = Exception.class)
    public String lockSeats(
            @ToolParam(description = "场次ID") Long scheduleId,
            @ToolParam(description = "座位ID数组") List<Long> seatIds
    ) {
        try {
            if (seatIds == null || seatIds.isEmpty()) {
                return "{\"success\":false,\"error\":\"未指定座位\"}";
            }

            // 1. 查询座位当前状态
            List<Seat> seats = seatMapper.selectListByQuery(
                    QueryWrapper.create().in(Seat::getId, seatIds)
            );

            if (seats.size() != seatIds.size()) {
                Set<Long> foundIds = seats.stream().map(Seat::getId).collect(Collectors.toSet());
                List<Long> missingIds = seatIds.stream()
                        .filter(id -> !foundIds.contains(id))
                        .collect(Collectors.toList());
                return "{\"success\":false,\"error\":\"座位不存在: " + missingIds + "\"}";
            }

            // 2. 检查哪些座位不可用
            List<Map<String, Object>> unavailableSeats = new ArrayList<>();
            List<Seat> availableSeats = new ArrayList<>();

            for (Seat seat : seats) {
                if ("available".equals(seat.getStatus())) {
                    availableSeats.add(seat);
                } else {
                    Map<String, Object> info = new HashMap<>();
                    info.put("seatId", seat.getId());
                    info.put("seatLabel", seat.getSeatLabel());
                    info.put("status", seat.getStatus());
                    unavailableSeats.add(info);
                }
            }

            if (!unavailableSeats.isEmpty()) {
                // 查找附近可用座位作为推荐
                List<Map<String, Object>> alternatives = findAlternatives(scheduleId, unavailableSeats);
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("conflictSeats", unavailableSeats);
                result.put("alternatives", alternatives);
                result.put("message", "部分座位已被占用，为您推荐附近可选座位～");
                return objectMapper.writeValueAsString(result);
            }

            // 3. 使用 Redis 分布式锁 + DB 乐观锁
            List<String> lockKeys = seatIds.stream()
                    .map(id -> "seat:lock:" + scheduleId + ":" + id)
                    .collect(Collectors.toList());

            List<RLock> acquiredLocks = new ArrayList<>();
            try {
                // 尝试获取所有 Redis 锁
                for (String lockKey : lockKeys) {
                    RLock lock = redissonClient.getLock(lockKey);
                    if (lock.tryLock(3, 15, TimeUnit.MINUTES)) {
                        acquiredLocks.add(lock);
                    } else {
                        // 释放已获取的锁
                        for (RLock acquired : acquiredLocks) {
                            try { acquired.unlock(); } catch (Exception ignored) {}
                        }
                        return "{\"success\":false,\"error\":\"座位锁定失败，请重试\"}";
                    }
                }

                // 4. DB 乐观锁：只更新 status='available' 的座位
                for (Seat seat : availableSeats) {
                    long updated = seatMapper.updateByQuery(
                            Seat.builder().status("locked").build(),
                            QueryWrapper.create()
                                    .eq(Seat::getId, seat.getId())
                                    .eq(Seat::getStatus, "available")
                    );
                    if (updated == 0) {
                        // 并发冲突
                        for (RLock acquired : acquiredLocks) {
                            try { acquired.unlock(); } catch (Exception ignored) {}
                        }
                        return "{\"success\":false,\"error\":\"手慢了！😅 座位 " + seat.getSeatLabel() + " 已被别人抢走\"}";
                    }
                }

                // 5. 锁定成功
                List<String> lockedLabels = availableSeats.stream()
                        .map(Seat::getSeatLabel)
                        .collect(Collectors.toList());

                BigDecimal totalPrice = availableSeats.stream()
                        .map(s -> BigDecimal.ZERO) // 价格由调用方根据 schedule 计算
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("lockedSeats", lockedLabels);
                result.put("lockedSeatIds", availableSeats.stream().map(Seat::getId).collect(Collectors.toList()));
                result.put("count", lockedLabels.size());
                result.put("totalPrice", totalPrice);
                result.put("message", "太棒了！🎉 已为您锁定 " + String.join("、", lockedLabels));

                log.info("lockSeats 成功: scheduleId={}, seats={}", scheduleId, lockedLabels);
                return objectMapper.writeValueAsString(result);

            } catch (Exception e) {
                // 异常时释放所有已获取的锁
                for (RLock acquired : acquiredLocks) {
                    try { acquired.unlock(); } catch (Exception ignored) {}
                }
                throw e;
            }

        } catch (Exception e) {
            log.error("lockSeats 失败", e);
            return "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    /**
     * 查找附近可用座位作为替代推荐
     */
    private List<Map<String, Object>> findAlternatives(Long scheduleId, List<Map<String, Object>> unavailableSeats) {
        try {
            // 查询所有可用座位，优先推荐同排相邻的
            List<Seat> availableSeats = seatMapper.selectListByQuery(
                    QueryWrapper.create()
                            .eq(Seat::getScheduleId, scheduleId)
                            .eq(Seat::getStatus, "available")
                            .orderBy(Seat::getRowNum, true)
                            .orderBy(Seat::getColNum, true)
                            .limit(10)
            );

            return availableSeats.stream().map(s -> {
                Map<String, Object> map = new HashMap<>();
                map.put("seatId", s.getId());
                map.put("seatLabel", s.getSeatLabel());
                map.put("zone", s.getZone());
                return map;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public String getToolName() {
        return "lockSeats";
    }

    @Override
    public String getDisplayName() {
        return "锁定座位";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        Long scheduleId = arguments.getLong("scheduleId");
        return String.format("[工具调用] 锁定座位 scheduleId=%d", scheduleId);
    }
}
