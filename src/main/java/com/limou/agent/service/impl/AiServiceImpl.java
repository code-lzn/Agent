package com.limou.agent.service.impl;

import cn.hutool.json.JSONUtil;
import com.limou.agent.ai.AiCodeGeneratorFactory;
import com.limou.agent.ai.StreamChunk;
import com.limou.agent.ai.movie.MovieAgentWorkflow;
import com.limou.agent.ai.movie.MovieStateManager;
import com.limou.agent.ai.movie.WorkflowDecision;
import com.limou.agent.model.entity.ChatHistory;
import com.limou.agent.service.AiService;
import com.limou.agent.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class AiServiceImpl implements AiService {

    @Resource
    private AiCodeGeneratorFactory aiCodeGeneratorFactory;

    @Resource
    private MovieStateManager movieStateManager;

    @Resource
    private MovieAgentWorkflow movieAgentWorkflow;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    @Qualifier("movieToolCallbacks")
    private ToolCallback[] movieToolCallbacks;

    @Value("classpath:prompts/movie-agent-prompt.md")
    private org.springframework.core.io.Resource moviePrompt;

    private String movieSystemPromptCache;

    @Override
    public String doChat(String message, String conversationId) {
        ChatClient chatClient = aiCodeGeneratorFactory.getOrCreateChatClient(Long.valueOf(conversationId));
        return chatClient.prompt().user(message).call().content();
    }

    @Override
    public Flux<String> doChatStream(String message, String conversationId) {
        ChatClient chatClient = aiCodeGeneratorFactory.getOrCreateChatClient(Long.valueOf(conversationId));
        return chatClient.prompt().user(message).stream().content();
    }

    @Override
    public <T> T doChatStructured(String message, String conversationId, Class<T> outputType) {
        ChatClient chatClient = aiCodeGeneratorFactory.getOrCreateChatClient(Long.valueOf(conversationId));
        return chatClient.prompt().user(message).call().entity(outputType);
    }

    @Override
    public String doAgentChat(String message, String conversationId) {
        return aiCodeGeneratorFactory.doAgentChat(message, conversationId);
    }

    @Override
    public <T> Optional<T> doAgentChatStructured(String message, String conversationId, Class<T> outputType) {
        return aiCodeGeneratorFactory.doAgentChatStructured(message, conversationId, outputType);
    }

    @Override
    public String doMovieChat(String message, String conversationId, Long userId) {
        log.info("MovieAgent 对话: conversationId={}, userId={}", conversationId, userId);

        // 1. GuardRail 安全检查
        WorkflowDecision decision = movieAgentWorkflow.execute(message, conversationId, userId);
        if (decision.isBlocked()) {
            return decision.getBlockMessage();
        }

        // 2. ReactAgent 全量工具，多轮 ReAct 循环
        String prompt = getMovieSystemPrompt();
        String response = aiCodeGeneratorFactory.doAgentChat(
                decision.getAugmentedMessage(), conversationId, prompt, movieToolCallbacks, "movie-agent");

        // 3. 持久化对话历史，避免重启失忆
        saveMovieChatHistory(conversationId, userId, message, response);

        movieStateManager.refreshTtl(conversationId);
        log.info("MovieAgent 响应长度: {}", response != null ? response.length() : 0);
        return response;
    }

    @Override
    public Flux<ServerSentEvent<String>> doMovieChatStream(String message, String conversationId, Long userId) {
        log.info("MovieAgent 流式对话: conversationId={}, userId={}", conversationId, userId);

        // 1. GuardRail 安全检查
        WorkflowDecision decision = movieAgentWorkflow.execute(message, conversationId, userId);

        if (decision.isBlocked()) {
            return Flux.just(
                    ServerSentEvent.<String>builder()
                            .data(JSONUtil.toJsonStr(Map.of("d", decision.getBlockMessage())))
                            .build(),
                    ServerSentEvent.<String>builder()
                            .event("done")
                            .data("")
                            .build())
                    .doFinally(signal -> movieStateManager.refreshTtl(conversationId));
        }

        // 2. ChatClient 流式 + 全量工具，Spring AI 内部处理多轮工具调用
        String prompt = getMovieSystemPrompt();
        StringBuilder fullResponse = new StringBuilder();

        return aiCodeGeneratorFactory.doAgentChatStream(
                decision.getAugmentedMessage(), conversationId, prompt, movieToolCallbacks, "movie-agent")
                .map(chunk -> {
                    fullResponse.append(chunk.content());
                    String jsonStr = JSONUtil.toJsonStr(Map.of("d", chunk.content()));
                    return ServerSentEvent.<String>builder()
                            .data(jsonStr)
                            .build();
                })
                .concatWith(Mono.just(
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("")
                                .build()))
                .doFinally(signal -> {
                    saveMovieChatHistory(conversationId, userId, message, fullResponse.toString());
                    movieStateManager.refreshTtl(conversationId);
                });
    }

    private void saveMovieChatHistory(String conversationId, Long userId, String userMessage, String aiResponse) {
        try {
            Long sessionId = Long.valueOf(conversationId);
            chatHistoryService.save(ChatHistory.builder()
                    .sessionId(sessionId).userId(userId)
                    .messageType("user").message(userMessage).build());
            chatHistoryService.save(ChatHistory.builder()
                    .sessionId(sessionId).userId(userId)
                    .messageType("ai").message(aiResponse).build());
        } catch (Exception e) {
            log.error("保存电影对话历史失败: conversationId={}", conversationId, e);
        }
    }

    private String getMovieSystemPrompt() {
        if (movieSystemPromptCache == null) {
            try {
                movieSystemPromptCache = moviePrompt.getContentAsString(StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("读取电影票提示词失败", e);
                movieSystemPromptCache = "你是一个电影票智能助手";
            }
        }
        return movieSystemPromptCache;
    }
}
