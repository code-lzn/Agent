package com.limou.agent.service.impl;

import cn.hutool.json.JSONUtil;
import com.limou.agent.ai.AiCodeGeneratorFactory;
import com.limou.agent.ai.movie.MovieStateManager;
import com.limou.agent.service.AiService;
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
    @Qualifier("movieToolCallbacks")
    private ToolCallback[] movieToolCallbacks;

    @Value("classpath:prompts/movie-agent-prompt.st")
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
        log.info("MovieAgent 对话: conversationId={}, userId={}, message={}",
                conversationId, userId, message);

        // 加载对话状态，注入上下文
        if (userId != null) {
            var state = movieStateManager.getState(conversationId);
            state.setUserId(userId);
            movieStateManager.saveState(conversationId, state);
        }
        String stateContext = movieStateManager.generateStatePrompt(conversationId);
        String augmentedMessage = stateContext + "\n【用户输入】\n" + message;

        // 懒加载 system prompt
        String prompt = getMovieSystemPrompt();

        String response = aiCodeGeneratorFactory.doAgentChat(
                augmentedMessage, conversationId, prompt, movieToolCallbacks, "movie-agent");

        // 刷新状态 TTL
        movieStateManager.refreshTtl(conversationId);

        log.info("MovieAgent 响应长度: {}", response != null ? response.length() : 0);
        return response;
    }

    @Override
    public Flux<ServerSentEvent<String>> doMovieChatStream(String message, String conversationId, Long userId) {
        log.info("MovieAgent 流式对话: conversationId={}, userId={}", conversationId, userId);

        if (userId != null) {
            var state = movieStateManager.getState(conversationId);
            state.setUserId(userId);
            movieStateManager.saveState(conversationId, state);
        }
        String stateContext = movieStateManager.generateStatePrompt(conversationId);
        String augmentedMessage = stateContext + "\n【用户输入】\n" + message;

        String prompt = getMovieSystemPrompt();

        return aiCodeGeneratorFactory.doAgentChatStream(
                augmentedMessage, conversationId, prompt, movieToolCallbacks, "movie-agent"
        ).map(chunk -> {
            String jsonStr = JSONUtil.toJsonStr(Map.of("d", chunk));
            return ServerSentEvent.<String>builder()
                    .data(jsonStr)
                    .build();
        }).concatWith(Mono.just(
                ServerSentEvent.<String>builder()
                        .event("done")
                        .data("")
                        .build()
        )).doFinally(signal -> movieStateManager.refreshTtl(conversationId));
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
