package com.limou.agent.ai;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.limou.agent.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

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
        log.info("创建新的 ReactAgent，name: {}", cacheKey);
        return ReactAgent.builder()
                .name(name)
                .model(chatModel)
                .systemPrompt(readSystemPrompt())
                .saver(new MemorySaver())
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
