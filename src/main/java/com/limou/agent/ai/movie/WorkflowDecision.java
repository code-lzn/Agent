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
