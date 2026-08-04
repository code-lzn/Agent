package com.limou.agent.service.impl;

import cn.hutool.json.JSONUtil;
import com.limou.agent.ai.AiCodeGeneratorFactory;
import com.limou.agent.ai.movie.ConversationContext;
import com.limou.agent.ai.movie.GuardRailResult;
import com.limou.agent.ai.movie.MovieGuardRail;
import com.limou.agent.ai.movie.MovieStateManager;
import com.limou.agent.ai.movie.MovieToolManager;
import com.limou.agent.ai.movie.WorkflowDecision;
import com.limou.agent.ai.movie.graph.MovieGraphWorkflow;
import com.limou.agent.ai.movie.graph.MovieIntent;
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

/**
 * AI 服务实现
 * <p>
 * GuardRail 策略：
 * <ul>
 *   <li>Smart 端点 → SmartMovieRouter 内置 GuardRail → 路由到 reactCore / graphCore（无二次校验）</li>
 *   <li>旧 ReAct 端点 → 入口层 GuardRail → reactCore</li>
 *   <li>旧 Graph 端点 → 入口层 GuardRail → graphCore</li>
 *   <li>旧 POST 端点 → 入口层 GuardRail → ReactAgent</li>
 * </ul>
 * 核心方法（reactCore / graphCore）不再做 GuardRail，避免重复校验。
 */
@Slf4j
@Service
public class AiServiceImpl implements AiService {

    @Resource
    private AiCodeGeneratorFactory aiCodeGeneratorFactory;

    @Resource
    private MovieStateManager movieStateManager;

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
    private MovieGuardRail movieGuardRail;

    @Resource
    @Qualifier("movieToolCallbacks")
    private ToolCallback[] movieToolCallbacks;

    @Value("classpath:prompts/movie-agent-prompt.md")
    private org.springframework.core.io.Resource moviePrompt;

    private String movieSystemPromptCache;

    // ==================== 通用 AI 接口 ====================

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

    // ==================== 电影票 Agent ====================

    // ---- GuardRail 辅助 ----

    /**
     * 构建 GuardRail 拦截的 SSE 响应流
     */
    private Flux<ServerSentEvent<String>> blockedFlux(String blockMessage, String conversationId) {
        return Flux.just(
                        ServerSentEvent.<String>builder()
                                .data(JSONUtil.toJsonStr(Map.of("d", blockMessage)))
                                .build(),
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("")
                                .build())
                .doFinally(signal -> movieStateManager.refreshTtl(conversationId));
    }

    // ---- ReAct 模式 ----

    @Override
    public String doMovieChat(String message, String conversationId, Long userId) {
        log.info("MovieAgent 对话: conversationId={}, userId={}", conversationId, userId);

        // GuardRail（入口层统一校验）
        GuardRailResult gr = movieGuardRail.check(message);
        if (!gr.allowed()) {
            return gr.message();
        }

        // ReactAgent 全量工具，多轮 ReAct 循环
        String prompt = getMovieSystemPrompt();
        String response = aiCodeGeneratorFactory.doAgentChat(
                message, conversationId, prompt, movieToolCallbacks, "movie-agent");

        saveMovieChatHistory(conversationId, userId, message, response);
        movieStateManager.refreshTtl(conversationId);
        log.info("MovieAgent 响应长度: {}", response != null ? response.length() : 0);
        return response;
    }

    @Override
    public Flux<ServerSentEvent<String>> doMovieChatStream(String message, String conversationId, Long userId) {
        // GuardRail（入口层统一校验）
        GuardRailResult gr = movieGuardRail.check(message);
        if (!gr.allowed()) {
            return blockedFlux(gr.message(), conversationId);
        }
        return reactCore(message, conversationId, userId, null);
    }

    /**
     * ReAct 核心 —— 无 GuardRail，由调用方保证已校验
     */
    private Flux<ServerSentEvent<String>> reactCore(
            String message, String conversationId, Long userId, String currentCity) {
        log.info("ReAct 流式: conversationId={}, userId={}", conversationId, userId);

        // 注入 conversationId 到 ThreadLocal，让工具方法能写回 ConversationState
        ConversationContext.set(conversationId);

        String prompt = withCurrentCity(getMovieSystemPrompt(), currentCity);
        StringBuilder fullResponse = new StringBuilder();

        return aiCodeGeneratorFactory.doAgentChatStream(
                        message, conversationId, prompt,
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
                    log.error("ReAct SSE 异常: conversationId={}", conversationId, e);
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
                    ConversationContext.clear();
                });
    }

    // ---- Graph 模式 ----

    /** intent → 中文工具名映射（由 MovieIntent 枚举统一维护） */
    private static final Map<String, String> INTENT_TOOL_NAMES = MovieIntent.toolDisplayNames();

    @Override
    public Flux<ServerSentEvent<String>> doMovieGraphChatStream(String message, String conversationId, Long userId) {
        // GuardRail（入口层统一校验）
        GuardRailResult gr = movieGuardRail.check(message);
        if (!gr.allowed()) {
            return blockedFlux(gr.message(), conversationId);
        }
        return graphCore(message, conversationId, userId, null);
    }

    /**
     * Graph 核心 —— 无 GuardRail，由调用方保证已校验
     */
    private Flux<ServerSentEvent<String>> graphCore(
            String message, String conversationId, Long userId, GraphIntentResult preclassifiedIntent) {
        log.info("GraphWorkflow 流式: conversationId={}, userId={}", conversationId, userId);

        // 1. Graph 工作流（同步阻塞，offload 到 boundedElastic，避免阻塞 Netty 线程）
        return Mono.fromCallable(() ->
                        movieGraphWorkflow.execute(message, conversationId, userId, preclassifiedIntent))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(decision -> {
                    if (decision.isBlocked()) {
                        return blockedFlux(decision.getBlockMessage(), conversationId);
                    }

        // 2. 准备流式回复
        String intent = decision.getIntent();
        String toolResult = decision.getToolResult();
        boolean hasTool = toolResult != null && !toolResult.isEmpty();
        String cardType = decision.getCardType();
        boolean hasCard = cardType != null && decision.getCardData() != null;

        ConversationState state = decision.getConvState() != null
                ? decision.getConvState()
                : movieStateManager.getState(conversationId);

        String stateContext = state.toPromptContext();
        // 有卡片时 {tool_result} 替换为简短提示 + 强约束，避免 LLM 把原始 JSON 当文本输出
        String toolResultForPrompt;
        String antiJsonRule;
        if (hasCard) {
            toolResultForPrompt = "已通过前端卡片展示";
            antiJsonRule = "\n\n## 重要规则\n工具执行结果已通过可视化卡片在前端展示，你**不要**重复输出数据。\n**严禁**在回复中出现任何JSON代码块。只需要用自然语言组织回复即可。";
        } else {
            toolResultForPrompt = hasTool ? toolResult : "无工具结果";
            antiJsonRule = "";
        }
        String prompt = (GraphResponseGenerator.RESPONSE_PROMPT + antiJsonRule)
                .replace("{intent}", intent != null ? intent : "chat")
                .replace("{tool_result}", toolResultForPrompt)
                .replace("{state}", stateContext)
                .replace("{input}", message);

        StringBuilder fullResponse = new StringBuilder();

        Flux<ServerSentEvent<String>> responseStream;

        // 前置事件流（卡片 > tool_start）
        Flux<ServerSentEvent<String>> prefixEvents = Flux.empty();

        if (hasCard) {
            // 卡片事件：前端渲染为可视化卡片
            Map<String, Object> cardPayload = new LinkedHashMap<>();
            cardPayload.put("type", "card");
            cardPayload.put("cardType", cardType);
            cardPayload.put("data", decision.getCardData());
            prefixEvents = prefixEvents.concatWith(Flux.just(
                    ServerSentEvent.<String>builder()
                            .data(JSONUtil.toJsonStr(cardPayload))
                            .build()));
        } else if (hasTool) {
            // 无卡片时显示旧的 tool_start 提示
            String displayName = INTENT_TOOL_NAMES.getOrDefault(decision.getToolName(), decision.getToolName());
            prefixEvents = prefixEvents.concatWith(Flux.just(
                    ServerSentEvent.<String>builder()
                            .data(JSONUtil.toJsonStr(Map.of(
                                    "d", "正在" + displayName + "...",
                                    "type", "tool_start",
                                    "toolName", decision.getToolName())))
                            .build()));
        }

        // 文本流：LLM 自然语言回复
        Flux<ServerSentEvent<String>> textStream = aiCodeGeneratorFactory
                .doSimpleChatStream(prompt, conversationId)
                .map(chunk -> {
                    fullResponse.append(chunk);
                    return ServerSentEvent.<String>builder()
                            .data(JSONUtil.toJsonStr(Map.of("d", chunk)))
                            .build();
                });

        responseStream = prefixEvents.concatWith(textStream);

        return responseStream
                .concatWith(Mono.just(
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("")
                                .build()))
                .onErrorResume(e -> {
                    log.error("Graph SSE 异常: conversationId={}", conversationId, e);
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
        });  // flatMapMany: offload Graph 阻塞调用到 boundedElastic
    }

    // ---- 智能路由 ----

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
                    // SmartRouter 内置 GuardRail，一次完成「安全检查 + 路由决策」
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

    /**
     * 路由分发 —— 内部方法，GuardRail 已在 SmartRouter 层完成
     */
    private Flux<ServerSentEvent<String>> routeStream(
            SmartRouteResult route, String message, String conversationId,
            Long userId, String currentCity) {
        log.info("SmartRouter: decision={}, reusedIntent={}, conversationId={}",
                route.decision(), route.intentResult() != null, conversationId);
        return switch (route.decision()) {
            case REACT -> reactCore(message, conversationId, userId, currentCity);
            case GRAPH -> graphCore(message, conversationId, userId, route.intentResult());
            case BLOCKED -> blockedFlux(route.blockMessage(), conversationId);
        };
    }

    // ==================== 辅助方法 ====================

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
