package com.limou.agent.ai.graph;

/**
 * 图边类型枚举
 * <p>
 * 定义图中两种边类型，用于区分固定路由和动态路由。
 *
 * @see StateGraph
 * @see ConditionalEdge
 */
public enum EdgeType {

    /** 普通边：固定从节点 A 路由到节点 B */
    STANDARD,

    /** 条件边：根据当前状态动态决定下一个节点 */
    CONDITIONAL
}
