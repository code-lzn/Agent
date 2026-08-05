package com.limou.agent.service;

import org.springframework.http.codec.ServerSentEvent;
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

    /**
     * 电影票 Agent 对话
     *
     * @param message        用户输入
     * @param conversationId 会话ID
     * @param userId         用户ID（用于偏好加载）
     * @return 响应结果
     */
    String doMovieChat(String message, String conversationId, Long userId);

    /**
     * 电影票 Agent 流式对话
     *
     * @param message        用户输入
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @return 流式响应
     */
    Flux<ServerSentEvent<String>> doMovieChatStream(String message, String conversationId, Long userId);

    /**
     * 电影票 Agent Graph 流式对话（StateGraph 工作流模式）
     * 代码控流程，LLM 只做意图识别和回复生成
     *
     * @param message        用户输入
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @return 流式响应
     */
    Flux<ServerSentEvent<String>> doMovieGraphChatStream(String message, String conversationId, Long userId);

    /**
     * 电影票 Agent 智能路由流式对话
     * 混合策略自动选择：信息齐全 → ReAct 一句完成，信息不足 → Graph 逐步引导
     *
     * @param message        用户输入
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @return 流式响应
     */
    Flux<ServerSentEvent<String>> doMovieSmartChatStream(
            String message, String conversationId, Long userId, String currentCity,
            Double lat, Double lng);
}
