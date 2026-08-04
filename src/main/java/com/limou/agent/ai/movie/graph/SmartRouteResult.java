package com.limou.agent.ai.movie.graph;

/**
 * 智能路由结果。Graph 分支复用路由阶段已经完成的意图识别，避免重复调用模型。
 */
public record SmartRouteResult(RouterDecision decision, GraphIntentResult intentResult,
                               String blockMessage) {

    public static SmartRouteResult react() {
        return new SmartRouteResult(RouterDecision.REACT, null, null);
    }

    public static SmartRouteResult graph(GraphIntentResult intentResult) {
        return new SmartRouteResult(RouterDecision.GRAPH, intentResult, null);
    }

    public static SmartRouteResult blocked(String message) {
        return new SmartRouteResult(RouterDecision.BLOCKED, null, message);
    }
}
