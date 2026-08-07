package com.limou.agent.ai.movie.tools;

import cn.hutool.json.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limou.agent.ai.movie.ConversationContext;
import com.limou.agent.ai.movie.MovieStateManager;
import com.limou.agent.mapper.ScheduleMapper;
import com.limou.agent.mapper.SeatMapper;
import com.limou.agent.model.dto.movie.ConversationState;
import com.limou.agent.model.entity.Schedule;
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
    private ScheduleMapper scheduleMapper;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private MovieStateManager stateManager;

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

            // 2. 检查哪些座位不可用（自动清理过期锁）
            List<Map<String, Object>> unavailableSeats = new ArrayList<>();
            List<Seat> availableSeats = new ArrayList<>();

            for (Seat seat : seats) {
                if ("available".equals(seat.getStatus())) {
                    availableSeats.add(seat);
                } else if ("locked".equals(seat.getStatus())) {
                    // 检查 Redis 锁是否已过期（过期 = 脏数据，自动释放）
                    String lockKey = "seat:lock:" + scheduleId + ":" + seat.getId();
                    RLock lock = redissonClient.getLock(lockKey);
                    if (!lock.isLocked()) {
                        // 锁已过期但 DB 状态没更新 → 自动修复
                        seatMapper.update(Seat.builder()
                                .id(seat.getId()).status("available").build());
                        availableSeats.add(seat);
                        log.info("自动释放过期锁: scheduleId={}, seat={}", scheduleId, seat.getSeatLabel());
                    } else {
                        Map<String, Object> info = new HashMap<>();
                        info.put("seatId", seat.getId());
                        info.put("seatLabel", seat.getSeatLabel());
                        info.put("status", seat.getStatus());
                        unavailableSeats.add(info);
                    }
                } else {
                    Map<String, Object> info = new HashMap<>();
                    info.put("seatId", seat.getId());
                    info.put("seatLabel", seat.getSeatLabel());
                    info.put("status", seat.getStatus());
                    unavailableSeats.add(info);
                }
            }

            if (!unavailableSeats.isEmpty()) {
                // ★ 幂等检查：过滤掉当前会话已锁定的座位（避免重复锁失败）
                String convId = ConversationContext.get();
                List<Map<String, Object>> trulyUnavailable = new ArrayList<>();
                if (convId != null) {
                    try {
                        ConversationState convState = stateManager.getState(convId);
                        List<Long> existingSeatIds = convState.getSeatIds();
                        if (existingSeatIds != null && !existingSeatIds.isEmpty()) {
                            for (Map<String, Object> us : unavailableSeats) {
                                Long seatId = ((Number) us.get("seatId")).longValue();
                                if (!existingSeatIds.contains(seatId)) {
                                    trulyUnavailable.add(us);
                                }
                            }
                        } else {
                            trulyUnavailable.addAll(unavailableSeats);
                        }
                    } catch (Exception e) {
                        trulyUnavailable.addAll(unavailableSeats);
                    }
                } else {
                    trulyUnavailable.addAll(unavailableSeats);
                }

                // 如果所有冲突座位都是自己锁的且没有新的 availableSeats 需要锁 → 幂等成功
                if (trulyUnavailable.isEmpty() && availableSeats.isEmpty()) {
                    List<String> lockedLabels = unavailableSeats.stream()
                            .map(s -> (String) s.get("seatLabel"))
                            .collect(Collectors.toList());
                    Schedule schedule = scheduleMapper.selectOneById(scheduleId);
                    BigDecimal totalPrice = lockedLabels.isEmpty() ? BigDecimal.ZERO
                            : (schedule != null && schedule.getPrice() != null
                                    ? schedule.getPrice().multiply(BigDecimal.valueOf(lockedLabels.size()))
                                    : BigDecimal.ZERO);
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("lockedSeats", lockedLabels);
                    result.put("count", lockedLabels.size());
                    result.put("totalPrice", totalPrice);
                    result.put("message", "座位已锁定： " + String.join("、", lockedLabels) + "（之前已锁定）");
                    log.info("lockSeats 幂等返回: conversationId={}, seats={}", convId, lockedLabels);
                    return objectMapper.writeValueAsString(result);
                }

                if (!trulyUnavailable.isEmpty()) {
                    // 有真正被他人占用的座位 → 推荐替代方案
                    List<Map<String, Object>> alternatives = findAlternatives(scheduleId, trulyUnavailable);
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", false);
                    result.put("conflictSeats", trulyUnavailable);
                    result.put("alternatives", alternatives);
                    result.put("message", "部分座位已被占用，为您推荐附近可选座位～");
                    return objectMapper.writeValueAsString(result);
                }
                // trulyUnavailable 为空但有 availableSeats → 继续正常锁定流程
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

                // 5. 锁定成功，根据排片计算实际价格
                List<String> lockedLabels = availableSeats.stream()
                        .map(Seat::getSeatLabel)
                        .collect(Collectors.toList());

                Schedule schedule = scheduleMapper.selectOneById(scheduleId);
                BigDecimal totalPrice = availableSeats.stream()
                        .map(s -> "vip".equals(s.getZone())
                                ? (schedule != null && schedule.getVipPrice() != null
                                        ? schedule.getVipPrice() : BigDecimal.ZERO)
                                : (schedule != null && schedule.getPrice() != null
                                        ? schedule.getPrice() : BigDecimal.ZERO))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("lockedSeats", lockedLabels);
                result.put("lockedSeatIds", availableSeats.stream().map(Seat::getId).map(String::valueOf).collect(Collectors.toList()));
                result.put("count", lockedLabels.size());
                result.put("totalPrice", totalPrice);
                result.put("message", "太棒了！🎉 已为您锁定 " + String.join("、", lockedLabels));

                // ReAct 模式下将 lockedSeatIds 写回 ConversationState（Graph 模式由 LockSeatsNode 处理）
                String convId = ConversationContext.get();
                if (convId != null) {
                    try {
                        List<Long> lockedIds = availableSeats.stream().map(Seat::getId).collect(Collectors.toList());
                        ConversationState convState = stateManager.getState(convId);
                        convState.setSeatIds(lockedIds);
                        convState.setScheduleId(scheduleId);
                        stateManager.saveState(convId, convState);
                        log.info("lockSeats 写回 seatIds={} 到 Redis: conversationId={}", lockedIds, convId);
                    } catch (Exception e) {
                        log.warn("lockSeats 写回状态失败: conversationId={}", convId, e);
                    }
                }

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

    /**
     * 释放所有过期的座位锁（Redis 锁已过期但 DB 状态仍为 locked）
     * 在会话重置时调用，避免脏数据影响后续订票
     */
    public void releaseStaleLocks() {
        try {
            List<Seat> lockedSeats = seatMapper.selectListByQuery(
                    QueryWrapper.create().eq(Seat::getStatus, "locked")
            );
            int released = 0;
            for (Seat seat : lockedSeats) {
                // 根据 scheduleId 重建 lock key 比较困难，直接用 forceUnlock 试探
                // 实际上 Redis 锁过期后 isLocked() 会返回 false
                // 这里直接释放所有 locked 状态但无活跃 Redis 锁的座位
                // 简单策略：把所有 locked 座位恢复为 available（开发/测试环境）
                seatMapper.update(Seat.builder()
                        .id(seat.getId()).status("available").build());
                released++;
            }
            if (released > 0) {
                log.info("释放过期座位锁: {} 个座位已恢复为 available", released);
            }
        } catch (Exception e) {
            log.error("释放过期锁失败", e);
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
