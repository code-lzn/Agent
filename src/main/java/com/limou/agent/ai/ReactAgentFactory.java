package com.limou.agent.ai;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.agent.Builder;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class ReactAgentFactory {

    private final DeepSeekChatModel deepseekChatModel;
    private final DashScopeChatModel dashScopeChatModel;
    private final ToolCallbackProvider mergedToolCallbacks;

    @Value("classpath:prompts/system-prompt.st")
    private Resource systemPrompt;

    public ReactAgent createAgent(String agentName) {
        return buildBuilder(agentName).build();
    }

    public <T> ReactAgent createAgent(Class<T> outputType, String agentName) {
        return buildBuilder(agentName).outputType(outputType).build();
    }

    private Builder buildBuilder(String agentName) {
        return ReactAgent.builder()
                .name(agentName)
                .model(deepseekChatModel)
                .systemPrompt(readSystemPrompt())
                .saver(new MemorySaver())
                .tools(mergedToolCallbacks.getToolCallbacks());
    }

    private String readSystemPrompt() {
        try {
            return systemPrompt.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("读取系统提示词文件失败: " + systemPrompt, e);
        }
    }
}
