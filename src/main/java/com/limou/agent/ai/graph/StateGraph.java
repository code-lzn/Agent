package com.limou.agent.ai.graph;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 状态图构建器 —— 声明式 DSL
 * <p>
 * 提供链式 API 定义图的节点、边和条件路由，最后通过 {@link #compile()}
 * 编译为可执行的 {@link CompiledGraph}。
 *
 * <pre>{@code
 * var graph = new StateGraph<MyState>()
 *     .addNode("step_a", new StepANode())
 *     .addNode("step_b", new StepBNode())
 *     .addEdge(START, "step_a")
 *     .addConditionalEdges("step_a", MyState::getChoice, Map.of("b", "step_b"))
 *     .compile();
 * MyState result = graph.invoke(initialState);
 * }</pre>
 *
 * @param <S> 状态类型
 */
public class StateGraph<S> {

    /** 起始节点名，只能作为边的起点 */
    public static final String START = "__START__";

    /** 终止节点名，只能作为边的终点 */
    public static final String END = "__END__";

    private final Map<String, GraphNode<S>> nodes = new LinkedHashMap<>();
    private final Map<String, EdgeDefinition<S>> edges = new LinkedHashMap<>();
    private boolean compiled = false;

    // ==================== 节点 ====================

    /**
     * 注册一个节点
     *
     * @param name 节点名（全局唯一，不能是 START/END）
     * @param node 节点实例
     */
    public StateGraph<S> addNode(String name, GraphNode<S> node) {
        checkNotCompiled();
        if (START.equals(name) || END.equals(name)) {
            throw new GraphStateException("不能使用保留节点名: " + name);
        }
        if (nodes.containsKey(name)) {
            throw new GraphStateException("节点已存在: " + name);
        }
        nodes.put(name, node);
        return this;
    }

    // ==================== 边 ====================

    /**
     * 添加固定边：from → to
     *
     * @param from 起点节点名（可以是 START）
     * @param to   终点节点名（可以是 END）
     */
    public StateGraph<S> addEdge(String from, String to) {
        checkNotCompiled();
        edges.put(from, new EdgeDefinition<>(EdgeType.STANDARD, to, null, null, null));
        return this;
    }

    /**
     * 添加条件边：根据状态动态选择下一个节点
     *
     * @param from      起点节点名
     * @param condition 路由条件函数，返回路由键
     * @param routeMap  路由键 → 目标节点名 的映射
     */
    public StateGraph<S> addConditionalEdges(String from,
                                              ConditionalEdge<S> condition,
                                              Map<String, String> routeMap) {
        return addConditionalEdges(from, condition, routeMap, END);
    }

    /**
     * 添加条件边（带默认路由）
     *
     * @param from          起点节点名
     * @param condition     路由条件函数
     * @param routeMap      路由键 → 目标节点名 的映射
     * @param defaultTarget 未匹配时的默认目标节点
     */
    public StateGraph<S> addConditionalEdges(String from,
                                              ConditionalEdge<S> condition,
                                              Map<String, String> routeMap,
                                              String defaultTarget) {
        checkNotCompiled();
        edges.put(from, new EdgeDefinition<>(EdgeType.CONDITIONAL, null, condition, routeMap, defaultTarget));
        return this;
    }

    // ==================== 编译 ====================

    /**
     * 编译图为可执行对象
     * <p>
     * 编译时校验：边引用的所有节点必须已注册，否则抛出 {@link GraphStateException}。
     *
     * @return 编译后的图
     * @throws GraphStateException 校验失败时
     */
    public CompiledGraph<S> compile() {
        checkNotCompiled();

        // 校验边引用的节点存在性
        for (var entry : edges.entrySet()) {
            String from = entry.getKey();
            EdgeDefinition<S> def = entry.getValue();

            if (!START.equals(from) && !nodes.containsKey(from)) {
                throw new GraphStateException("边起点节点不存在: " + from);
            }

            if (def.type == EdgeType.STANDARD) {
                validateNodeExists(def.target);
            } else {
                for (String target : def.routeMap.values()) {
                    validateNodeExists(target);
                }
                if (def.defaultTarget != null) {
                    validateNodeExists(def.defaultTarget);
                }
            }
        }

        compiled = true;
        return new CompiledGraph<>(
                Collections.unmodifiableMap(new LinkedHashMap<>(nodes)),
                Collections.unmodifiableMap(new LinkedHashMap<>(edges)));
    }

    private void validateNodeExists(String name) {
        if (!END.equals(name) && !nodes.containsKey(name)) {
            throw new GraphStateException("边目标节点不存在: " + name);
        }
    }

    private void checkNotCompiled() {
        if (compiled) {
            throw new GraphStateException("StateGraph 已经编译，不能再修改");
        }
    }

    // ==================== 内部类型 ====================

    /**
     * 边定义 —— 封装边的类型和路由逻辑
     */
    static class EdgeDefinition<S> {
        final EdgeType type;
        final String target;                        // STANDARD 边目标
        final ConditionalEdge<S> condition;         // CONDITIONAL 边条件
        final Map<String, String> routeMap;         // CONDITIONAL 边路由表
        final String defaultTarget;                 // CONDITIONAL 边兜底

        EdgeDefinition(EdgeType type, String target, ConditionalEdge<S> condition,
                       Map<String, String> routeMap, String defaultTarget) {
            this.type = type;
            this.target = target;
            this.condition = condition;
            this.routeMap = routeMap;
            this.defaultTarget = defaultTarget;
        }

        /**
         * 根据状态解析下一个节点
         */
        String resolve(S state) {
            if (type == EdgeType.STANDARD) {
                return target;
            }
            String key = condition.route(state);
            if (routeMap != null && routeMap.containsKey(key)) {
                return routeMap.get(key);
            }
            return defaultTarget != null ? defaultTarget : END;
        }
    }
}
