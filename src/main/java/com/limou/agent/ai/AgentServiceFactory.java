package com.limou.agent.ai;

import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limou.agent.ai.tools.ToolManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
public class AgentServiceFactory {
    @Resource
    @Qualifier(value = "deepSeekChatClient")
    private ChatClient deepSeekchatClient;
    @Resource
    private ReactAgentFactory reactAgentFactory;
    @Resource
    private ToolCallback[] toolCallbacks;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private ToolCallbackProvider mergedToolCallbacks;
//    @Resource
//    private ToolCallbackProvider toolCallbackProvider;

    // ---- ChatClient 系列 ----

    public String doChat(String message, String conversationId) {
        String content = deepSeekchatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
        return content;
    }

    public <T> T doChatStructured(String message, String conversationId, Class<T> outputType) {
        T result = deepSeekchatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .entity(outputType);
        log.info("Chat 结构化: {}", result);
        return result;
    }

    // ---- ReactAgent 系列 ----

    public String doAgentChat(String message, String conversationId) {
        ReactAgent agent = reactAgentFactory.createAgent("reAct-agent");
        try {
            String result = agent.call(message, buildConfig(conversationId)).getText();
            log.info("Agent 响应: {}", result);
            return result;
        } catch (GraphRunnerException e) {
            log.error("Agent 执行失败", e);
            return "Agent 执行出错: " + e.getMessage();
        }
    }
//
    public <T> Optional<T> doAgentChatStructured(String message, String conversationId,
                                                  Class<T> outputType) {
        ReactAgent agent = reactAgentFactory.createAgent(outputType, "structured-ReAct-agent");
        try {
            String json = agent.call(message, buildConfig(conversationId)).getText();
            log.info("Agent 结构化: {}", json);
//            return JSONUtil.toBean(json,outputType);
            return Optional.ofNullable(objectMapper.readValue(json, outputType));
        } catch (GraphRunnerException e) {
            log.error("Agent 结构化输出失败", e);
            return Optional.empty();
        } catch (Exception e) {
            log.error("JSON 解析失败", e);
            return Optional.empty();
        }
    }

    /**
     * 为每个会话定义一个唯一的 RunnableConfig，用于 Agent 状态持久化。
     * threadId 相当于 sessionId，相同 ID 的调用共享同一份记忆和推理上下文。
     */
    private RunnableConfig buildConfig(String conversationId) {
        return RunnableConfig.builder()
                .threadId(conversationId)
                .build();
    }
}
