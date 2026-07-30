package com.limou.agent.ai.movie;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 电影票 Agent 工作流
 * GuardRail 安全检查 → 全量工具交由 LLM 自主编排
 * 适用场景：用户一句话完成订票，LLM 自动链式调用工具
 */
@Slf4j
@Component
public class MovieAgentWorkflow {

    @Resource
    private MovieGuardRail movieGuardRail;

    /**
     * 执行工作流 —— GuardRail 安全检查
     * LLM 携带全部工具自行决策调用顺序，无需步骤拆分
     */
    public WorkflowDecision execute(String message, String conversationId, Long userId) {
        log.info("Workflow 执行: conversationId={}", conversationId);

        GuardRailResult result = movieGuardRail.check(message);
        if (!result.allowed()) {
            log.warn("GuardRail 拦截: {}", result.message());
            return WorkflowDecision.blocked(result.message());
        }

        log.info("Workflow 通过 GuardRail");
        return WorkflowDecision.proceed(message);
    }
}
