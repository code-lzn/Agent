package com.limou.agent.ai.movie.graph.nodes;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.limou.agent.ai.graph.GraphNode;
import com.limou.agent.ai.movie.MovieStateManager;
import com.limou.agent.ai.movie.graph.MovieGraphState;
import com.limou.agent.ai.movie.graph.MovieIntent;
import com.limou.agent.ai.movie.tools.LockSeatsTool;
import com.limou.agent.model.dto.movie.ConversationState;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 锁定座位节点
 * <p>
 * 执行后会将 lockedSeatIds 写回 ConversationState 并持久化到 Redis，
 * 确保下一轮 create_order 能直接从状态中获取已锁定的座位。
 */
@Slf4j
public class LockSeatsNode implements GraphNode<MovieGraphState> {

    private final LockSeatsTool tool;
    private final MovieStateManager stateManager;

    public LockSeatsNode(LockSeatsTool tool, MovieStateManager stateManager) {
        this.tool = tool;
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

        String result = tool.lockSeats(convState.getScheduleId(), convState.getSeatIds());

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
}
