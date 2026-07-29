package com.limou.agent.ai;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.limou.agent.service.ChatHistoryService;
import jakarta.annotation.Resource;
import org.redisson.api.RedissonClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
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
import java.util.Optional;

import reactor.core.publisher.Flux;

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

    private final Cache<Long, ChatClient> clientCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) ->
                    log.debug("ChatClient 缓存移除，sessionId: {}, 原因: {}", key, cause))
            .build();

    private final Cache<String, ReactAgent> agentCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) ->
                    log.debug("ReactAgent 缓存移除，key: {}, 原因: {}", key, cause))
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
        ReactAgent agent = agentCache.get(cacheKey, key ->
                ReactAgent.builder()
                        .name(agentName)
                        .model(chatModel)
                        .systemPrompt(systemPrompt)
                        .saver(RedisSaver.builder().redisson(redissonClient).build())
                        .tools(tools)
                        .build()
        );
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
     * 通用 Agent 流式对话 —— 使用 ChatClient 实现真正的 token 级流式输出
     */
    public Flux<String> doAgentChatStream(String message, String conversationId,
                                          String systemPrompt, ToolCallback[] tools, String agentName) {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
        chatHistoryService.loadChatHistory(Long.valueOf(conversationId), chatMemory, 20);
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .defaultToolCallbacks(tools)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        return chatClient.prompt()
                .user(message)
                .stream()
                .content();
    }

    /** 清除指定 Agent 缓存 */
    public void evictAgentCache(String conversationId, String agentName) {
        agentCache.invalidate(conversationId + ":" + agentName);
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
