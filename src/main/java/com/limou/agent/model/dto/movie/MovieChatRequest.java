package com.limou.agent.model.dto.movie;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 电影票 Agent 对话请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovieChatRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户输入消息 */
    private String message;

    /** 会话ID（用于多轮对话上下文） */
    private String conversationId;

    /** 用户ID（用于偏好加载和历史订单） */
    private Long userId;
}
