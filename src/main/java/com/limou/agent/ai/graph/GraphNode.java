package com.limou.agent.ai.graph;

/**
 * 图节点接口 —— 状态图的执行单元
 * <p>
 * 每个节点接收当前状态，执行业务逻辑，返回更新后的状态。
 * 节点应该是无副作用的纯函数（除必要的 I/O 外），以便于测试和复用。
 *
 * @param <S> 状态类型，贯穿整个图的执行过程
 */
@FunctionalInterface
public interface GraphNode<S> {

    /**
     * 执行节点逻辑
     *
     * @param state 当前状态
     * @return 更新后的状态
     */
    S execute(S state);
}
