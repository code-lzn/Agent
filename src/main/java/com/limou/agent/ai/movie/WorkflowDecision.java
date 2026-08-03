package com.limou.agent.ai.movie;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 工作流决策结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDecision implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否被 GuardRail 拦截 */
    private boolean blocked;

    /** 拦截原因（blocked=true 时有值） */
    private String blockMessage;

    /** 用户消息（透传） */
    private String augmentedMessage;

    /** Graph 模式: 识别的意图 */
    private String intent;

    /** Graph 模式: 工具执行结果 JSON */
    private String toolResult;

    /** Graph 模式: 工具名称 */
    private String toolName;

    /** Graph 模式: 会话状态 JSON（用于回复生成） */
    private String stateJson;

    public static WorkflowDecision blocked(String message) {
        return WorkflowDecision.builder()
                .blocked(true)
                .blockMessage(message)
                .build();
    }

    public static WorkflowDecision proceed(String message) {
        return WorkflowDecision.builder()
                .blocked(false)
                .augmentedMessage(message)
                .build();
    }
}
