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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    /** 座位标签解析："5排6座" → {5, 6} */
    private static final Pattern ROW_COL_PATTERN = Pattern.compile("(\\d+)排(\\d+)座");

    /**
     * 查找可用座位作为替代推荐
     * <p>
     * ★ 修复"推荐很差"：原实现只取全厅 rowNum/colNum 升序前 10 个可用座位，
     * 推荐出来的几乎都是最前排/最靠边的座位（如"1排1座、1排2座"），完全不贴近用户想坐的区域。
     * <p>
     * 新策略：
     * ① 就近：优先推荐与冲突座位同排、且列号紧邻冲突列号的连续可用座位（用户原本想坐的区域）；
     * ② 居中：跨排推荐时按"离中心排近 → 列号靠近影厅正中心"排序，不再推荐边角座；
     * ③ 成块：同一排只返回一个连续可用块，保证前端"每排相邻两位配对成方案"能配出真正相邻的座位
     *    （否则混入不连续座位会配出"5排4座+5排8座"这种奇怪方案）；
     * ④ 覆盖：按用户想要的票数返回 3~5 个方案的量，让用户有得选。
     */
    private List<Map<String, Object>> findAlternatives(Long scheduleId, List<Map<String, Object>> unavailableSeats) {
        try {
            // 拉取整厅可用座位（一个厅 80~200 座，全量内存计算足够快）
            List<Seat> allSeats = seatMapper.selectListByQuery(
                    QueryWrapper.create()
                            .eq(Seat::getScheduleId, scheduleId)
                            .eq(Seat::getStatus, "available"));
            if (allSeats.isEmpty()) {
                return Collections.emptyList();
            }

            // 按行分组（TreeMap 保证行号有序）
            TreeMap<Integer, List<Seat>> byRow = allSeats.stream()
                    .filter(s -> s.getRowNum() != null)
                    .collect(Collectors.groupingBy(Seat::getRowNum, TreeMap::new, Collectors.toList()));

            // 冲突锚点：解析"5排6座"拿用户原本想坐的行列（作为就近推荐的基准）
            int anchorRow = -1;
            int anchorCol = -1;
            if (unavailableSeats != null && !unavailableSeats.isEmpty()) {
                for (Map<String, Object> us : unavailableSeats) {
                    String label = String.valueOf(us.get("seatLabel"));
                    Matcher m = ROW_COL_PATTERN.matcher(label == null ? "" : label);
                    if (m.find()) {
                        anchorRow = Integer.parseInt(m.group(1));
                        anchorCol = Integer.parseInt(m.group(2));
                        break;
                    }
                }
            }

            // 影厅中心行 / 中心列
            int centerRow = (byRow.firstKey() + byRow.lastKey()) / 2;
            int maxCol = byRow.values().stream()
                    .flatMap(List::stream)
                    .mapToInt(s -> s.getColNum() != null ? s.getColNum() : 0)
                    .max().orElse(0);
            double midCol = maxCol / 2.0;

            // 用户想要的票数（方案至少要能配出相邻的 2 张；单座冲突也按 2 张推荐，保证前端能配出方案）
            int needCount = Math.max(2, unavailableSeats == null ? 1 : unavailableSeats.size());

            // 行排序：冲突同排优先 → 离冲突排近 → 离中心排近
            // （anchorRow 在解析循环中被赋值，非 effectively-final，先提成 final 副本供 lambda 捕获）
            final int baseRow = anchorRow > 0 ? anchorRow : centerRow;
            List<Integer> rows = new ArrayList<>(byRow.keySet());
            rows.sort(Comparator
                    .comparingInt((Integer r) -> Math.abs(r - baseRow))
                    .thenComparingInt(r -> Math.abs(r - centerRow)));

            List<Map<String, Object>> result = new ArrayList<>();
            Map<String, Object> bestSingle = null;   // 全厅兜底：凑不出连座时的最佳单座
            double bestSingleDist = Double.MAX_VALUE;
            final int MAX_ALTS = 12;
            for (int r : rows) {
                if (result.size() >= MAX_ALTS) {
                    break;
                }
                List<Seat> rowSeats = new ArrayList<>(byRow.get(r));
                rowSeats.sort(Comparator.comparingInt(s -> s.getColNum() != null ? s.getColNum() : 0));
                List<List<Seat>> runs = splitConsecutiveRuns(rowSeats);
                if (runs.isEmpty()) {
                    continue;
                }
                // 同排优先选"列中心最靠近锚点列"的块；跨排优先选"列中心最靠近影厅中心列"的块
                double targetCol = (anchorRow == r && anchorCol > 0) ? anchorCol : midCol;
                List<Seat> bestRun = null;
                double bestDist = Double.MAX_VALUE;
                for (List<Seat> run : runs) {
                    double runCenter = (run.get(0).getColNum() + run.get(run.size() - 1).getColNum()) / 2.0;
                    double dist = Math.abs(runCenter - targetCol);
                    // 单座无法配成前端方案，仅作全厅兜底
                    if (run.size() == 1 && dist < bestSingleDist) {
                        bestSingleDist = dist;
                        bestSingle = toAltMap(run.get(0));
                    }
                    // 只推荐 ≥2 座的连续块，保证前端能配出"相邻两位"方案
                    if (run.size() >= 2 && dist < bestDist - 1e-9) {
                        bestDist = dist;
                        bestRun = run;
                    }
                }
                if (bestRun == null) {
                    continue;
                }
                // 块内取 needCount 张连续座位（优先取列中心最靠近目标列的子段；块不够长则整块都给）
                List<Seat> take = bestWindow(bestRun, needCount, targetCol);
                for (Seat s : take) {
                    result.add(toAltMap(s));
                }
            }
            // 全厅都凑不出连座时才退而推荐最佳单座，避免给空列表
            if (result.isEmpty() && bestSingle != null) {
                result.add(bestSingle);
            }
            return result;
        } catch (Exception e) {
            log.warn("findAlternatives 失败: scheduleId={}", scheduleId, e);
            return Collections.emptyList();
        }
    }

    /** 同一行内，把按列号升序的可用座位切分成多个"列号连续"的可用块 */
    private List<List<Seat>> splitConsecutiveRuns(List<Seat> rowSeats) {
        List<List<Seat>> runs = new ArrayList<>();
        List<Seat> cur = new ArrayList<>();
        int prevCol = Integer.MIN_VALUE;
        for (Seat s : rowSeats) {
            int col = s.getColNum() != null ? s.getColNum() : 0;
            if (!cur.isEmpty() && col - prevCol != 1) {
                runs.add(cur);
                cur = new ArrayList<>();
            }
            cur.add(s);
            prevCol = col;
        }
        if (!cur.isEmpty()) {
            runs.add(cur);
        }
        return runs;
    }

    /** 在连续块内取 need 张、列中心最靠近 targetCol 的子段（块不够长则整块返回） */
    private List<Seat> bestWindow(List<Seat> run, int need, double targetCol) {
        if (run.size() <= need) {
            return run;
        }
        List<Seat> best = null;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i + need <= run.size(); i++) {
            int left = run.get(i).getColNum();
            int right = run.get(i + need - 1).getColNum();
            double center = (left + right) / 2.0;
            double dist = Math.abs(center - targetCol);
            if (dist < bestDist - 1e-9) {
                bestDist = dist;
                best = run.subList(i, i + need);
            }
        }
        return best != null ? best : run;
    }

    /** 替代座位转前端可用的 map（seatId 用字符串，避免雪花 ID 超出 JS 精度） */
    private Map<String, Object> toAltMap(Seat s) {
        Map<String, Object> map = new HashMap<>();
        map.put("seatId", String.valueOf(s.getId()));
        map.put("seatLabel", s.getSeatLabel());
        map.put("zone", s.getZone());
        return map;
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
