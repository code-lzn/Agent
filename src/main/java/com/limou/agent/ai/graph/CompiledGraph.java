package com.limou.agent.ai.graph;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 编译后的状态图 —— 可执行的有向图
 * <p>
 * 由 {@link StateGraph#compile()} 生成，通过 {@link #invoke(Object)} 执行。
 * 执行过程：从 START 出发，依次执行节点并通过边路由到下一节点，直到抵达 END。
 *
 * @param <S> 状态类型
 */
@Slf4j
public class CompiledGraph<S> {

    private final Map<String, GraphNode<S>> nodes;
    private final Map<String, StateGraph.EdgeDefinition<S>> edges;

    CompiledGraph(Map<String, GraphNode<S>> nodes,
                  Map<String, StateGraph.EdgeDefinition<S>> edges) {
        this.nodes = nodes;
        this.edges = edges;
    }

    /**
     * 执行图
     * <p>
     * 从 START 出发，遍历图直到 END。每一步：
     * <ol>
     *   <li>如果当前节点有对应的 Node，执行它并更新状态</li>
     *   <li>查找当前节点的边定义，解析下一个节点</li>
     *   <li>如果没有边定义，终止执行</li>
     * </ol>
     *
     * @param initialState 初始状态
     * @return 最终状态
     */
    public S invoke(S initialState) {
        String current = StateGraph.START;
        S state = initialState;

        while (!StateGraph.END.equals(current)) {
            // 执行当前节点
            GraphNode<S> node = nodes.get(current);
            if (node != null) {
                try {
                    state = node.execute(state);
                } catch (Exception e) {
                    log.error("节点 [{}] 执行异常", current, e);
                    throw new GraphStateException("节点 [" + current + "] 执行失败: " + e.getMessage(), e);
                }
            }

            // 路由到下一个节点
            StateGraph.EdgeDefinition<S> edgeDef = edges.get(current);
            if (edgeDef == null) {
                log.debug("节点 [{}] 没有出边，停止执行", current);
                break;
            }

            String next = edgeDef.resolve(state);
            log.debug("路由: [{}] → [{}]", current, next);
            current = next;
        }

        return state;
    }

    /**
     * 获取图中所有节点的只读映射
     */
    public Map<String, GraphNode<S>> getNodes() {
        return nodes;
    }
}
