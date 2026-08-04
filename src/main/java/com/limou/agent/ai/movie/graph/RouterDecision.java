package com.limou.agent.ai.movie.graph;

/**
 * 智能路由决策结果
 */
public enum RouterDecision {
    /** 走 ReAct 模式——LLM 自主链式调用工具，适合信息齐全的一句话订票 */
    REACT,
    /** 走 Graph 模式——代码控流程逐步引导，适合信息不足需要追问 */
    GRAPH,
    /** 被 GuardRail 拦截——直接返回拒绝消息 */
    BLOCKED
}
