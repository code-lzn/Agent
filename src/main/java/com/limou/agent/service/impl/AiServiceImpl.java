package com.limou.agent.service.impl;

import cn.hutool.json.JSONUtil;
import com.limou.agent.ai.AiCodeGeneratorFactory;
import com.limou.agent.ai.movie.MovieAgentWorkflow;
import com.limou.agent.ai.movie.MovieStateManager;
import com.limou.agent.ai.movie.MovieToolManager;
import com.limou.agent.ai.movie.WorkflowDecision;
import com.limou.agent.ai.movie.graph.MovieGraphWorkflow;
import com.limou.agent.ai.movie.graph.GraphResponseGenerator;
import com.limou.agent.ai.movie.graph.GraphIntentResult;
import com.limou.agent.ai.movie.graph.SmartMovieRouter;
import com.limou.agent.ai.movie.graph.SmartRouteResult;
import com.limou.agent.model.entity.ChatHistory;
import com.limou.agent.model.dto.movie.ConversationState;
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
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

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
    private MovieGraphWorkflow movieGraphWorkflow;

    @Resource
    private GraphResponseGenerator graphResponseGenerator;

    @Resource
    private SmartMovieRouter smartMovieRouter;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private MovieToolManager movieToolManager;

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
        return doMovieChatStream(message, conversationId, userId, null);
    }

    private Flux<ServerSentEvent<String>> doMovieChatStream(
            String message, String conversationId, Long userId, String currentCity) {
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
        // 工具调用时发射 tool_start 事件 → 前端显示"正在搜索影片..."
        String prompt = withCurrentCity(getMovieSystemPrompt(), currentCity);
        StringBuilder fullResponse = new StringBuilder();

        return aiCodeGeneratorFactory.doAgentChatStream(
                decision.getAugmentedMessage(), conversationId, prompt,
                movieToolCallbacks, movieToolManager.getToolDisplayNames(), "movie-agent")
                .map(chunk -> {
                    if ("tool_start".equals(chunk.type())) {
                        String msg = "正在" + chunk.toolDisplayName() + "...";
                        return ServerSentEvent.<String>builder()
                                .data(JSONUtil.toJsonStr(Map.of(
                                        "d", msg,
                                        "type", "tool_start",
                                        "toolName", chunk.toolName())))
                                .build();
                    }
                    fullResponse.append(chunk.content());
                    return ServerSentEvent.<String>builder()
                            .data(JSONUtil.toJsonStr(Map.of("d", chunk.content())))
                            .build();
                })
                .concatWith(Mono.just(
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("")
                                .build()))
                .onErrorResume(e -> {
                    log.error("ReAct SSE 流式输出异常: conversationId={}", conversationId, e);
                    return Flux.just(
                            ServerSentEvent.<String>builder()
                                    .data(JSONUtil.toJsonStr(Map.of("d", "抱歉，出了一点问题：" + e.getMessage())))
                                    .build(),
                            ServerSentEvent.<String>builder()
                                    .event("done")
                                    .data("")
                                    .build());
                })
                .doFinally(signal -> {
                    saveMovieChatHistory(conversationId, userId, message, fullResponse.toString());
                    movieStateManager.refreshTtl(conversationId);
                });
    }

    /** intent → 中文工具名映射 */
    private static final Map<String, String> INTENT_TOOL_NAMES = Map.of(
            "search_movie", "搜索影片",
            "search_cinema", "搜索影院",
            "search_schedule", "搜索场次",
            "get_seat_map", "获取座位图",
            "lock_seats", "锁定座位",
            "create_order", "创建订单",
            "pay_order", "支付订单",
            "get_preference", "获取偏好");

    @Override
    public Flux<ServerSentEvent<String>> doMovieGraphChatStream(String message, String conversationId, Long userId) {
        return doMovieGraphChatStream(message, conversationId, userId, null);
    }

    private Flux<ServerSentEvent<String>> doMovieGraphChatStream(
            String message, String conversationId, Long userId, GraphIntentResult preclassifiedIntent) {
        log.info("GraphWorkflow 流式对话: conversationId={}, userId={}", conversationId, userId);

        // === 1. Graph 工作流: GuardRail → 意图识别 → 工具执行 ===
        WorkflowDecision decision = movieGraphWorkflow.execute(
                message, conversationId, userId, preclassifiedIntent);

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

        // === 2. 准备流式回复 ===
        String intent = decision.getIntent();
        String toolResult = decision.getToolResult();
        boolean hasTool = toolResult != null && !toolResult.isEmpty();

        // 从 decision 中恢复状态（避免重复 getState）
        ConversationState state = decision.getStateJson() != null
                ? ConversationState.fromJson(decision.getStateJson())
                : movieStateManager.getState(conversationId);

        // 构建 prompt
        String stateContext = state.toPromptContext();
        String prompt = GraphResponseGenerator.RESPONSE_PROMPT
                .replace("{intent}", intent != null ? intent : "chat")
                .replace("{tool_result}", hasTool ? toolResult : "无工具结果")
                .replace("{state}", stateContext)
                .replace("{input}", message);

        // 累积流式文本，用于正确保存历史
        StringBuilder fullResponse = new StringBuilder();

        // 构建 SSE 流
        Flux<ServerSentEvent<String>> responseStream;

        if (hasTool) {
            String displayName = INTENT_TOOL_NAMES.getOrDefault(decision.getToolName(), decision.getToolName());
            Flux<ServerSentEvent<String>> toolEvent = Flux.just(
                    ServerSentEvent.<String>builder()
                            .data(JSONUtil.toJsonStr(Map.of(
                                    "d", "正在" + displayName + "...",
                                    "type", "tool_start",
                                    "toolName", decision.getToolName())))
                            .build());
            Flux<ServerSentEvent<String>> textStream = aiCodeGeneratorFactory
                    .doSimpleChatStream(prompt, conversationId)
                    .map(chunk -> {
                        fullResponse.append(chunk);
                        return ServerSentEvent.<String>builder()
                                .data(JSONUtil.toJsonStr(Map.of("d", chunk)))
                                .build();
                    });
            responseStream = toolEvent.concatWith(textStream);
        } else {
            responseStream = aiCodeGeneratorFactory
                    .doSimpleChatStream(prompt, conversationId)
                    .map(chunk -> {
                        fullResponse.append(chunk);
                        return ServerSentEvent.<String>builder()
                                .data(JSONUtil.toJsonStr(Map.of("d", chunk)))
                                .build();
                    });
        }

        return responseStream
                .concatWith(Mono.just(
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("")
                                .build()))
                .onErrorResume(e -> {
                    log.error("Graph SSE 流式输出异常: conversationId={}", conversationId, e);
                    return Flux.just(
                            ServerSentEvent.<String>builder()
                                    .data(JSONUtil.toJsonStr(Map.of("d", "抱歉，出了一点问题：" + e.getMessage())))
                                    .build(),
                            ServerSentEvent.<String>builder()
                                    .event("done")
                                    .data("")
                                    .build());
                })
                .doFinally(signal -> {
                    // 保存的是流式输出的真实内容，不是阻塞调用的结果
                    saveMovieChatHistory(conversationId, userId, message, fullResponse.toString());
                    movieStateManager.refreshTtl(conversationId);
                });
    }

    @Override
    public Flux<ServerSentEvent<String>> doMovieSmartChatStream(
            String message, String conversationId, Long userId, String currentCity) {
        String normalizedCity = normalizeCity(currentCity);

        ServerSentEvent<String> initialStatus = ServerSentEvent.<String>builder()
                .data(JSONUtil.toJsonStr(Map.of(
                        "d", "正在理解你的需求",
                        "type", "status")))
                .build();

        Flux<ServerSentEvent<String>> routedStream = Mono.fromCallable(() -> {
                    ConversationState state = movieStateManager.getState(conversationId);
                    if (userId != null) state.setUserId(userId);
                    if (normalizedCity != null) state.setCurrentCity(normalizedCity);
                    movieStateManager.saveState(conversationId, state);
                    return smartMovieRouter.route(message, state);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(route -> routeStream(
                        route, message, conversationId, userId, normalizedCity));

        return Flux.concat(Flux.just(initialStatus), routedStream).onErrorResume(e -> {
            log.error("SmartStream 异常: conversationId={}", conversationId, e);
            return Flux.just(
                    ServerSentEvent.<String>builder()
                            .data(JSONUtil.toJsonStr(Map.of("d", "抱歉，出了一点问题：" + e.getMessage())))
                            .build(),
                    ServerSentEvent.<String>builder()
                            .event("done")
                            .data("")
                            .build());
        });
    }

    private Flux<ServerSentEvent<String>> routeStream(
            SmartRouteResult route, String message, String conversationId,
            Long userId, String currentCity) {
        log.info("SmartRouter: decision={}, reusedIntent={}, conversationId={}",
                route.decision(), route.intentResult() != null, conversationId);
        return switch (route.decision()) {
            case REACT -> doMovieChatStream(message, conversationId, userId, currentCity);
            case GRAPH -> doMovieGraphChatStream(
                    message, conversationId, userId, route.intentResult());
        };
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

    private String normalizeCity(String city) {
        if (city == null || city.isBlank() || "请选择城市".equals(city)) return null;
        String normalized = city.trim();
        return normalized.endsWith("市")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }

    private String withCurrentCity(String prompt, String currentCity) {
        if (currentCity == null) return prompt;
        return prompt + "\n\n## 当前运行上下文\n用户当前城市：" + currentCity
                + "。当用户提到附近、本地或就近时，优先使用该城市筛选影院。";
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
