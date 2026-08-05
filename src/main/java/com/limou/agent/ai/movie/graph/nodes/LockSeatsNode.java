package com.limou.agent.ai.movie.graph.nodes;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.limou.agent.ai.graph.GraphNode;
import com.limou.agent.ai.movie.MovieStateManager;
import com.limou.agent.ai.movie.graph.MovieGraphState;
import com.limou.agent.ai.movie.graph.MovieIntent;
import com.limou.agent.ai.movie.tools.GetSeatMapTool;
import com.limou.agent.ai.movie.tools.LockSeatsTool;
import com.limou.agent.model.dto.movie.ConversationState;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 锁定座位节点
 * <p>
 * 支持两种模式：
 * 1. 用户指定具体座位（seatIds 非空）→ 直接锁定
 * 2. 用户表达选座偏好（"帮我选中间3个座位"，有 preferredSeatZone/ticketCount 但无 seatIds）
 *    → 自动调 getSeatMap 拿座位图，按偏好选连续可用座位，再锁定
 * <p>
 * 执行后会将 lockedSeatIds 写回 ConversationState 并持久化到 Redis，
 * 确保下一轮 create_order 能直接从状态中获取已锁定的座位。
 */
@Slf4j
public class LockSeatsNode implements GraphNode<MovieGraphState> {

    private final LockSeatsTool tool;
    private final GetSeatMapTool getSeatMapTool;
    private final MovieStateManager stateManager;

    public LockSeatsNode(LockSeatsTool tool, GetSeatMapTool getSeatMapTool, MovieStateManager stateManager) {
        this.tool = tool;
        this.getSeatMapTool = getSeatMapTool;
        this.stateManager = stateManager;
    }

    @Override
    public MovieGraphState execute(MovieGraphState state) {
        if (state.isBlocked()) {
            return state;
        }

        ConversationState convState = state.getConvState();

        if (convState.getScheduleId() == null) {
            state.setToolResult("{\"error\":\"请先选择场次\"}");
            state.setToolName(MovieIntent.LOCK_SEATS.getCode());
            return state;
        }

        List<Long> seatIds = convState.getSeatIds();

        // ★ 自动选座：用户要求"帮我选座位"（有选座偏好或票数）但未指定具体座位
        if ((seatIds == null || seatIds.isEmpty())
                && (has(convState.getPreferredSeatZone()) || convState.getTicketCount() != null)) {
            int count = convState.getTicketCount() != null && convState.getTicketCount() > 0
                    ? convState.getTicketCount()
                    : 1;
            String zone = has(convState.getPreferredSeatZone()) ? convState.getPreferredSeatZone() : "中间";
            String seatMapJson = getSeatMapTool.getSeatMap(convState.getScheduleId());
            seatIds = autoPickSeats(seatMapJson, zone, count);
            if (seatIds.isEmpty()) {
                state.setToolResult("{\"success\":false,\"conflictSeats\":[],\"message\":\"没有找到可选的连续座位，请手动选座\"}");
                state.setToolName(MovieIntent.LOCK_SEATS.getCode());
                return state;
            }
            convState.setSeatIds(seatIds);
            log.info("LockSeats 自动选座: zone={}, count={}, seatIds={}",
                    zone, count, seatIds);
        }

        if (seatIds == null || seatIds.isEmpty()) {
            state.setToolResult("{\"error\":\"请先选择座位\"}");
            state.setToolName(MovieIntent.LOCK_SEATS.getCode());
            return state;
        }

        String result = tool.lockSeats(convState.getScheduleId(), seatIds);

        state.setToolResult(result);
        state.setToolName(MovieIntent.LOCK_SEATS.getCode());

        // 解析工具返回，将 lockedSeatIds 写回 ConversationState 并持久化到 Redis
        try {
            JSONObject json = JSONUtil.parseObj(result);
            if (json.getBool("success", false)) {
                List<Long> lockedIds = json.getJSONArray("lockedSeatIds")
                        .stream()
                        .map(o -> Long.valueOf(o.toString()))
                        .collect(Collectors.toList());
                if (!lockedIds.isEmpty()) {
                    convState.setSeatIds(lockedIds);
                    stateManager.saveState(state.getConversationId(), convState);
                    log.info("LockSeats 写回 seatIds={}: conversationId={}", lockedIds, state.getConversationId());
                }
            }
        } catch (Exception e) {
            log.warn("LockSeats 结果解析失败，跳过状态写回: conversationId={}", state.getConversationId(), e);
        }

        return state;
    }

    /**
     * 根据选座偏好自动挑选连续可用座位。
     *
     * @param seatMapJson getSeatMap 返回的座位图 JSON
     * @param zone        偏好区域：中间/靠前/靠后/靠边（默认中间）
     * @param count       需要的连续座位数
     * @return 选中的 seatId 列表（找不到返回空）
     */
    private List<Long> autoPickSeats(String seatMapJson, String zone, int count) {
        List<Long> picked = new ArrayList<>();
        try {
            JSONObject map = JSONUtil.parseObj(seatMapJson);
            JSONArray grid = map.getJSONArray("seatGrid");
            if (grid == null || grid.isEmpty() || count <= 0) {
                return picked;
            }

            int rowCount = grid.size();
            int startRow;
            int endRow;
            switch (zone == null ? "" : zone) {
                case "靠前" -> {
                    startRow = 0;
                    endRow = Math.max(1, (int) Math.ceil(rowCount * 0.3));
                }
                case "靠后" -> {
                    startRow = (int) Math.floor(rowCount * 0.7);
                    endRow = rowCount;
                }
                case "靠边" -> {
                    startRow = 0;
                    endRow = rowCount;
                }
                default -> { // 中间
                    startRow = (int) Math.floor(rowCount * 0.3);
                    endRow = Math.min(rowCount, (int) Math.ceil(rowCount * 0.7));
                    if (endRow <= startRow) {
                        startRow = 0;
                        endRow = rowCount;
                    }
                }
            }

            for (int r = startRow; r < endRow; r++) {
                JSONArray row = grid.getJSONArray(r);
                if (row == null || row.size() < count) {
                    continue;
                }
                // 收集该行可用座位
                List<JSONObject> available = new ArrayList<>();
                for (int c = 0; c < row.size(); c++) {
                    Object cell = row.get(c);
                    if (cell == null) {
                        continue;
                    }
                    JSONObject seat = (JSONObject) cell;
                    if ("available".equals(seat.getStr("status"))) {
                        available.add(seat);
                    }
                }
                if (available.size() < count) {
                    continue;
                }
                // 找 count 个相邻（colNum 连续）的可用座位
                picked = findConsecutive(available, count);
                if (!picked.isEmpty()) {
                    return picked;
                }
            }
        } catch (Exception e) {
            log.warn("自动选座失败: zone={}, count={}", zone, count, e);
        }
        return picked;
    }

    /** 在按列升序排列的可用座位里找 count 个相邻座位 */
    private List<Long> findConsecutive(List<JSONObject> available, int count) {
        List<JSONObject> sorted = new ArrayList<>(available);
        sorted.sort((a, b) -> Integer.compare(
                a.getInt("colNum", 0), b.getInt("colNum", 0)));
        for (int i = 0; i <= sorted.size() - count; i++) {
            boolean consecutive = true;
            for (int j = 1; j < count; j++) {
                int prev = sorted.get(i + j - 1).getInt("colNum", 0);
                int cur = sorted.get(i + j).getInt("colNum", 0);
                if (cur - prev != 1) {
                    consecutive = false;
                    break;
                }
            }
            if (consecutive) {
                List<Long> ids = new ArrayList<>();
                for (int j = 0; j < count; j++) {
                    Long sid = sorted.get(i + j).getLong("seatId");
                    if (sid != null) {
                        ids.add(sid);
                    }
                }
                if (ids.size() == count) {
                    return ids;
                }
            }
        }
        return new ArrayList<>();
    }

    private boolean has(String s) {
        return s != null && !s.isEmpty();
    }
}
