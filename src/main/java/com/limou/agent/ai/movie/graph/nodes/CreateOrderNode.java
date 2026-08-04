package com.limou.agent.ai.movie.graph.nodes;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.limou.agent.ai.graph.GraphNode;
import com.limou.agent.ai.movie.MovieStateManager;
import com.limou.agent.ai.movie.graph.MovieGraphState;
import com.limou.agent.ai.movie.graph.MovieIntent;
import com.limou.agent.ai.movie.tools.CreateOrderTool;
import com.limou.agent.model.dto.movie.ConversationState;
import lombok.extern.slf4j.Slf4j;

/**
 * 创建订单节点
 * <p>
 * 执行后会将 orderId 写回 ConversationState 并持久化到 Redis，
 * 确保下一轮对话（如用户说"支付"）能直接从状态中获取 orderId。
 */
@Slf4j
public class CreateOrderNode implements GraphNode<MovieGraphState> {

    private final CreateOrderTool tool;
    private final MovieStateManager stateManager;

    public CreateOrderNode(CreateOrderTool tool, MovieStateManager stateManager) {
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
            state.setToolName(MovieIntent.CREATE_ORDER.getCode());
            return state;
        }

        String result = tool.createOrder(
                convState.getScheduleId(),
                convState.getSeatIds(),
                convState.getUserId());

        state.setToolResult(result);
        state.setToolName(MovieIntent.CREATE_ORDER.getCode());

        // 解析工具返回，将 orderId 写回 ConversationState 并持久化到 Redis
        try {
            JSONObject json = JSONUtil.parseObj(result);
            if (json.getBool("success", false)) {
                Long orderId = json.getLong("orderId");
                if (orderId != null) {
                    convState.setOrderId(orderId);
                    stateManager.saveState(state.getConversationId(), convState);
                    log.info("CreateOrder 写回 orderId={}: conversationId={}", orderId, state.getConversationId());
                }
            }
        } catch (Exception e) {
            log.warn("CreateOrder 结果解析失败，跳过状态写回: conversationId={}", state.getConversationId(), e);
        }

        return state;
    }
}
