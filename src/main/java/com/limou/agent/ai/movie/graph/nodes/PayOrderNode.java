package com.limou.agent.ai.movie.graph.nodes;

import com.limou.agent.ai.graph.GraphNode;
import com.limou.agent.ai.movie.graph.MovieGraphState;
import com.limou.agent.ai.movie.graph.MovieIntent;
import com.limou.agent.ai.movie.tools.PayOrderTool;
import com.limou.agent.model.dto.movie.ConversationState;
import lombok.extern.slf4j.Slf4j;

/**
 * 支付订单节点
 */
@Slf4j
public class PayOrderNode implements GraphNode<MovieGraphState> {

    private final PayOrderTool tool;

    public PayOrderNode(PayOrderTool tool) {
        this.tool = tool;
    }

    @Override
    public MovieGraphState execute(MovieGraphState state) {
        if (state.isBlocked()) {
            return state;
        }

        ConversationState convState = state.getConvState();

        if (convState.getOrderId() == null) {
            state.setToolResult("{\"error\":\"您还没有待支付的订单\"}");
            state.setToolName(MovieIntent.PAY_ORDER.getCode());
            return state;
        }

        String result = tool.payOrder(convState.getOrderId(), "alipay");
        state.setToolResult(result);
        state.setToolName(MovieIntent.PAY_ORDER.getCode());
        log.info("PayOrder 完成: conversationId={}", state.getConversationId());
        return state;
    }
}