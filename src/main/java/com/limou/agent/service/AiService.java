package com.limou.agent.service;

import reactor.core.publisher.Flux;

import java.util.Optional;

public interface AiService {
    /**
     * 聊天接口
     *
     * @param message          用户输入
     * @param conversationId 会话ID
     * @return 响应结果
     */

    String doChat(String message, String conversationId);
    /**
     * 流式接口
     *
     * @param message          用户输入
     * @param conversationId 会话ID
     * @return 响应结果
     */

    Flux<String> doChatStream(String message, String conversationId);

    /**
     * 结构化接口
     *
     * @param message          用户输入
     * @param conversationId 会话ID
     * @param outputType     输出类型
     * @return 响应结果
     */
    <T> T doChatStructured(String message, String conversationId, Class<T> outputType);
    /**
     * 智能助手接口
     *
     * @param message          用户输入
     * @param conversationId 会话ID
     * @return 响应结果
     */
    String doAgentChat(String message, String conversationId);
    /**
     * 智能助手结构化接口
     *
     * @param message          用户输入
     * @param conversationId 会话ID
     * @param outputType     输出类型
     * @return 响应结果
     */
    <T> Optional<T> doAgentChatStructured(String message, String conversationId, Class<T> outputType);
}
