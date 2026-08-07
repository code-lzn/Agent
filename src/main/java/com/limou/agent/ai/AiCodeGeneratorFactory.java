package com.limou.agent.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import com.limou.agent.ai.movie.EventEmittingToolCallback;
import com.limou.agent.rag.DocumentRagService;
import com.limou.agent.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Slf4j
@Component
public class AiCodeGeneratorFactory {

    @Resource
    private DeepSeekChatModel chatModel;
    @Resource
    private ChatMemoryRepository chatMemoryRepository;
    @Resource
    private ChatHistoryService chatHistoryService;
    @Resource
    private DocumentRagService documentRagService;

    /** doSimpleChatStream ChatClient 缓存 —— 按 conversationId，带对话记忆 + RAG */
    private final Cache<String, ChatClient> simpleChatClientCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) ->
                    log.debug("SimpleChatClient 缓存移除，key: {}, 原因: {}", key, cause))
            .build();

    /** doAgentChatStream 基础 ChatClient 缓存 —— 带对话记忆 + RAG，不含 systemPrompt/tools */
    private final Cache<String, ChatClient> streamAgentChatClientCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) ->
                    log.debug("StreamAgentChatClient 缓存移除，key: {}, 原因: {}", key, cause))
            .build();

    /** 工具显示名缓存 —— 按 agentName，避免每次请求遍历 tools 数组重新计算 toolName → displayName */
    private final Cache<String, Map<String, String>> toolDisplayNamesCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(Duration.ofMinutes(60))
            .build();

    /**
     * 通用 Agent 流式对话 —— ChatClient.stream() 实现 token 级流式输出
     * Spring AI 内部自动处理多轮工具调用，工具执行期间会短暂停顿
     *
     * @param toolDisplayNames 工具英文名 → 中文显示名的映射（可为 null，null 时从缓存自动计算）
     */
    public Flux<StreamChunk> doAgentChatStream(String message, String conversationId,
            String systemPrompt, ToolCallback[] tools,
            Map<String, String> toolDisplayNames, String agentName) {
        Sinks.Many<StreamChunk> toolSink = Sinks.many().replay().all();

        // ★ 工具显示名缓存：首次由调用方传入（如 movieToolManager.getToolDisplayNames()），之后走缓存
        Map<String, String> displayNames;
        if (toolDisplayNames != null && !toolDisplayNames.isEmpty()) {
            displayNames = toolDisplayNamesCache.get(agentName, key -> toolDisplayNames);
        } else {
            displayNames = toolDisplayNamesCache.get(agentName,
                    key -> buildToolDisplayNames(tools));
        }

        // 包装工具（per-request，因为需要 per-request Sink）
        ToolCallback[] wrappedTools = wrapToolsForStream(tools, displayNames, toolSink);

        // ★ Caffeine 缓存：基础 ChatClient（对话记忆 + RAG），systemPrompt + tools 通过 mutate 叠加
        String cacheKey = conversationId + ":" + agentName;
        ChatClient baseClient = streamAgentChatClientCache.get(cacheKey,
                key -> buildBaseStreamChatClient(conversationId));

        ChatClient chatClient = baseClient.mutate()
                .defaultSystem(systemPrompt)
                .defaultToolCallbacks(wrappedTools)
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

    /** 包装工具回调 —— 已抽取出，displayNames 可走缓存 */
    private ToolCallback[] wrapToolsForStream(ToolCallback[] tools,
            Map<String, String> displayNames, Sinks.Many<StreamChunk> toolSink) {
        ToolCallback[] wrapped = new ToolCallback[tools.length];
        for (int i = 0; i < tools.length; i++) {
            String toolName = tools[i].getToolDefinition().name();
            String displayName = displayNames.getOrDefault(toolName, toolName);
            wrapped[i] = new EventEmittingToolCallback(tools[i], displayName, toolSink);
        }
        return wrapped;
    }

    /** 从 tools 数组构建 toolName → displayName 映射（缓存未命中时调用一次） */
    private Map<String, String> buildToolDisplayNames(ToolCallback[] tools) {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        for (ToolCallback tool : tools) {
            String name = tool.getToolDefinition().name();
            map.put(name, name); // displayName 默认与 toolName 相同（调用方可通过参数覆盖）
        }
        return map;
    }

    /** 构建流式 Agent 基础 ChatClient —— 对话记忆 + RAG，不含 systemPrompt 和 tools */
    private ChatClient buildBaseStreamChatClient(String conversationId) {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
        chatHistoryService.loadChatHistory(Long.valueOf(conversationId), chatMemory, 20);
        return ChatClient.builder(chatModel)
                .defaultAdvisors(
                        QuestionAnswerAdvisor.builder(documentRagService.getVectorStore()).build(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    /**
     * 简单流式对话，不带工具，但带对话记忆 + RAG
     * 用于 Graph 工作流的回复生成环节
     * ★ Caffeine 缓存 ChatClient（按 conversationId），避免每次重建 Memory/加载历史
     */
    public Flux<String> doSimpleChatStream(String prompt, String conversationId) {
        ChatClient chatClient = simpleChatClientCache.get(conversationId, key -> {
            MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                    .chatMemoryRepository(chatMemoryRepository)
                    .maxMessages(20)
                    .build();
            chatHistoryService.loadChatHistory(Long.valueOf(conversationId), chatMemory, 20);
            return ChatClient.builder(chatModel)
                    .defaultAdvisors(
                            MessageChatMemoryAdvisor.builder(chatMemory).build(),
                            QuestionAnswerAdvisor.builder(documentRagService.getVectorStore()).build())
                    .build();
        });
        return chatClient.prompt().user(prompt).stream().content();
    }

    /** 清除 Agent 流式缓存（按 conversationId + agentName） */
    public void evictAgentStreamCache(String conversationId, String agentName) {
        streamAgentChatClientCache.invalidate(conversationId + ":" + agentName);
    }

    /** 清除简单流式对话缓存（按 conversationId） */
    public void evictSimpleChatClientCache(String conversationId) {
        simpleChatClientCache.invalidate(conversationId);
    }
}
