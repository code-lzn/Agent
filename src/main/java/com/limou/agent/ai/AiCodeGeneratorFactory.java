package com.limou.agent.ai;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.limou.agent.rag.DocumentRagService;
import com.limou.agent.service.ChatHistoryService;
import jakarta.annotation.Resource;
import org.redisson.api.RedissonClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Slf4j
@Component
public class AiCodeGeneratorFactory {

    @Value("classpath:prompts/system-prompt.st")
    private org.springframework.core.io.Resource systemPrompt;

    @Resource
    private DeepSeekChatModel chatModel;
    @Resource
    @Qualifier("mergedToolCallbacks")
    private ToolCallbackProvider mergedToolCallbacks;
    @Resource
    private ChatMemoryRepository chatMemoryRepository;
    @Resource
    private ChatHistoryService chatHistoryService;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private ObjectMapper objectMapper;
//    @Resource
    private DocumentRagService documentRagService;

    private final Cache<Long, ChatClient> clientCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> log.debug("ChatClient 缓存移除，sessionId: {}, 原因: {}", key, cause))
            .build();

    private final Cache<String, ReactAgent> agentCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> log.debug("ReactAgent 缓存移除，key: {}, 原因: {}", key, cause))
            .build();

    // ---- ChatClient ----

    public ChatClient getOrCreateChatClient(Long sessionId) {
        return clientCache.get(sessionId, this::createChatClient);
    }

    // ---- ReactAgent ----

    private ReactAgent getOrCreateAgent(String conversationId, String name) {
        return agentCache.get(conversationId + ":" + name, key -> createAgent(key, name));
    }

    private ChatClient createChatClient(Long sessionId) {
        log.info("为 sessionId: {} 创建新的 ChatClient", sessionId);
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
        chatHistoryService.loadChatHistory(sessionId, chatMemory, 20);
        return ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .defaultToolCallbacks(mergedToolCallbacks)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    private ReactAgent createAgent(String cacheKey, String name) {

        return ReactAgent.builder()
                .name(name)
                .model(chatModel)
                .systemPrompt(readSystemPrompt())
                .saver(RedisSaver.builder().redisson(redissonClient).build())
                .tools(mergedToolCallbacks.getToolCallbacks())
                .build();
    }

    public String doAgentChat(String message, String conversationId) {
        ReactAgent agent = getOrCreateAgent(conversationId, "reAct-agent");
        try {
            String result = agent.call(message, buildConfig(conversationId)).getText();
            log.info("Agent 响应: {}", result);
            return result;
        } catch (GraphRunnerException e) {
            log.error("Agent 执行失败", e);
            return "Agent 执行出错: " + e.getMessage();
        }
    }

    /**
     * 通用 Agent 对话 —— 支持自定义系统提示词、工具集和 Agent 名称
     * 供电影票 Agent 等子模块复用
     */
    public String doAgentChat(String message, String conversationId,
            String systemPrompt, ToolCallback[] tools, String agentName) {
        String cacheKey = conversationId + ":" + agentName;
        ReactAgent agent = agentCache.get(cacheKey, key -> ReactAgent.builder()
                .name(agentName)
                .model(chatModel)
                .systemPrompt(systemPrompt)
                .saver(RedisSaver.builder().redisson(redissonClient).build())
                .tools(tools)
                .build());
        try {
            String result = agent.call(message, buildConfig(conversationId)).getText();
            log.info("{} 响应: {}", agentName, result);
            return result;
        } catch (GraphRunnerException e) {
            log.error("{} 执行失败", agentName, e);
            return "Agent 执行出错: " + e.getMessage();
        }
    }

    /**
     * 通用 Agent 流式对话 —— ChatClient.stream() 实现 token 级流式输出
     * Spring AI 内部自动处理多轮工具调用，工具执行期间会短暂停顿
     *
     * @param toolDisplayNames 工具英文名 → 中文显示名的映射，用于流式输出"正在XXX..."提示
     */
    public Flux<StreamChunk> doAgentChatStream(String message, String conversationId,
            String systemPrompt, ToolCallback[] tools,
            Map<String, String> toolDisplayNames, String agentName) {
        Sinks.Many<StreamChunk> toolSink = Sinks.many().replay().all();

        // 包装工具，调用时发射 tool_start 事件
        ToolCallback[] wrappedTools = new ToolCallback[tools.length];
        for (int i = 0; i < tools.length; i++) {
            String toolName = tools[i].getToolDefinition().name();
            String displayName = toolDisplayNames != null
                    ? toolDisplayNames.getOrDefault(toolName, toolName)
                    : toolName;
            wrappedTools[i] = new EventEmittingToolCallback(tools[i], displayName, toolSink);
        }

        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
        chatHistoryService.loadChatHistory(Long.valueOf(conversationId), chatMemory, 20);
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .defaultToolCallbacks(wrappedTools)
                .defaultAdvisors(
                        // QuestionAnswerAdvisor.builder(documentRagService.getVectorStore()).build(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();

        Flux<StreamChunk> chatStream = chatClient.prompt()
                .user(message)
                .stream()
                .chatResponse()
                .flatMap(response -> {
                    if (response.getResults() == null || response.getResults().isEmpty()) {
                        return Flux.empty();
                    }
                    String text = response.getResults().get(0).getOutput().getText();
                    if (text != null && !text.isEmpty()) {
                        return Flux.just(StreamChunk.text(text));
                    }
                    return Flux.empty();
                })
                .doFinally(signal -> toolSink.tryEmitComplete());

        return Flux.merge(toolSink.asFlux(), chatStream);
    }

    /** ToolCallback 包装器：在工具调用时向 Sink 发射 tool_start 事件，并兜底 JSON 解析异常。
     *  对 lockSeats / createOrder / payOrder 等工具，还发射 card 事件供前端渲染交互卡片 */
    private static final class EventEmittingToolCallback implements ToolCallback {
        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EventEmittingToolCallback.class);

        private final ToolCallback delegate;
        private final String displayName;
        private final Sinks.Many<StreamChunk> sink;

        EventEmittingToolCallback(ToolCallback delegate, String displayName, Sinks.Many<StreamChunk> sink) {
            this.delegate = delegate;
            this.displayName = displayName;
            this.sink = sink;
        }

        @Override
        public String call(String toolInput) {
            String toolName = delegate.getToolDefinition().name();
            sink.tryEmitNext(StreamChunk.toolStart(toolName, displayName));
            try {
                String result = delegate.call(toolInput);
                // ★ 对 card-worthy 工具结果也发射 card 事件给前端
                String cardType = inferCardType(toolName, result);
                if (cardType != null) {
                    sink.tryEmitNext(StreamChunk.card(cardType, result));
                }
                return result;
            } catch (Exception e) {
                log.warn("工具调用参数解析失败: tool={}, error={}",
                        delegate.getToolDefinition().name(), e.getMessage());
                return "{\"error\":\"参数格式错误，请检查 JSON 格式后重试: " + e.getMessage() + "\"}";
            }
        }

        /** 根据 toolName 和结果推断前端卡片类型 */
        private static String inferCardType(String toolName, String result) {
            if (result == null || result.isBlank()) return null;
            boolean success = result.contains("\"success\":true") || result.contains("\"success\": true");
            return switch (toolName) {
                case "searchFilms"      -> "film_list";
                case "searchCinemas"    -> "cinema_list";
                case "searchSchedules"  -> "schedule_list";
                case "getSeatMap"       -> "seat_map";
                case "lockSeats"        -> success ? "seats_confirmed" : "seat_alternatives";
                case "createOrder"      -> success ? "order_detail" : null;
                case "payOrder"         -> success ? "payment_form" : null;
                default                 -> null;
            };
        }

        @Override
        public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }
    }

    /** 清除指定 Agent 缓存 */
    public void evictAgentCache(String conversationId, String agentName) {
        agentCache.invalidate(conversationId + ":" + agentName);
    }

    /**
     * 简单流式对话，不带工具、不带 RAG，但带对话记忆
     * 用于 Graph 工作流的回复生成环节
     */
    public Flux<String> doSimpleChatStream(String prompt, String conversationId) {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
        chatHistoryService.loadChatHistory(Long.valueOf(conversationId), chatMemory, 20);
        return ChatClient.builder(chatModel).build()
                .prompt().user(prompt)
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .stream().content();
    }

    public <T> Optional<T> doAgentChatStructured(String message, String conversationId, Class<T> outputType) {
        ReactAgent agent = getOrCreateAgent(conversationId, "structured-ReAct-agent");
        try {
            String json = agent.call(message, buildConfig(conversationId)).getText();
            log.info("Agent 结构化: {}", json);
            return Optional.ofNullable(objectMapper.readValue(json, outputType));
        } catch (GraphRunnerException e) {
            log.error("Agent 结构化输出失败", e);
            return Optional.empty();
        } catch (Exception e) {
            log.error("JSON 解析失败", e);
            return Optional.empty();
        }
    }

    private RunnableConfig buildConfig(String conversationId) {
        return RunnableConfig.builder()
                .threadId(conversationId)
                .build();
    }

    private String readSystemPrompt() {
        try {
            return systemPrompt.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("读取系统提示词文件失败: " + systemPrompt, e);
        }
    }
}
