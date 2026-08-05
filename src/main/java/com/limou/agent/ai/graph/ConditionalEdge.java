package com.limou.agent.ai.graph;

/**
 * 条件边接口 —— 状态驱动的动态路由
 * <p>
 * 根据当前状态计算下一个目标节点名称。
 * 与 {@link StateGraph#addConditionalEdges} 配合使用，
 * route 返回的字符串会通过路由映射表解析为实际节点名。
 *
 * @param <S> 状态类型
 * @see StateGraph#addConditionalEdges(String, ConditionalEdge, java.util.Map)
 */
@FunctionalInterface
public interface ConditionalEdge<S> {

    /**
     * 根据状态决定路由目标
     *
     * @param state 当前状态
     * @return 路由键（由 addConditionalEdges 的 routeMap 映射到目标节点名）
     */
    String route(S state);
}
