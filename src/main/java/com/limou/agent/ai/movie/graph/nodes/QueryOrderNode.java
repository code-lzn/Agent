package com.limou.agent.ai.movie.graph.nodes;

import com.limou.agent.ai.graph.GraphNode;
import com.limou.agent.ai.movie.graph.MovieGraphState;
import com.limou.agent.ai.movie.tools.QueryOrderTool;
import lombok.extern.slf4j.Slf4j;

/**
 * 查询订单节点 — 用户问"看看订单""订单详情"时执行
 */
@Slf4j
public class QueryOrderNode implements GraphNode<MovieGraphState> {

    private final QueryOrderTool tool;

    public QueryOrderNode(QueryOrderTool tool) {
        this.tool = tool;
    }

    @Override
    public MovieGraphState execute(MovieGraphState state) {
        if (state.isBlocked()) return state;

        var convState = state.getConvState();
        Long orderId = convState != null ? convState.getOrderId() : null;
        Long userId = state.getUserId();

        if (orderId == null || userId == null) {
            state.setBlocked(true);
            state.setBlockMessage("暂无订单信息");
            return state;
        }

        try {
            String result = tool.queryOrder(orderId, userId);
            state.setToolResult(result);
            state.setToolName("query_order");
            log.info("QueryOrderNode: orderId={}, result={}", orderId,
                    result != null && result.length() > 200 ? result.substring(0, 200) + "…" : result);
        } catch (Exception e) {
            log.error("QueryOrderNode 失败: orderId={}", orderId, e);
            state.setToolResult("{\"success\":false,\"error\":\"" + e.getMessage() + "\"}");
            state.setToolName("query_order");
        }
        return state;
    }
}
