package com.limou.agent.ai;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
@RequiredArgsConstructor
public class EasyChatAgentFactory {

    @Value("classpath:prompts/system-prompt.st")
    private Resource systemPrompt;

    private final DeepSeekChatModel deepseekChatModel;
    private final DashScopeChatModel dashScopeChatModel;
    @Qualifier("mergedToolCallbacks")
    private final ToolCallbackProvider mergedToolCallbacks;

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(10)
                .build();
    }

    private ChatClient buildChatClient(ChatModel model, ChatMemory chatMemory) {
        return ChatClient.builder(model)
                .defaultSystem(systemPrompt)
                .defaultToolCallbacks(mergedToolCallbacks)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    @Bean
    public ChatClient deepSeekChatClient(ChatMemory chatMemory) {
        return buildChatClient(deepseekChatModel, chatMemory);
    }

    @Bean
    public ChatClient dashScopeChatClient(ChatMemory chatMemory) {
        return buildChatClient(dashScopeChatModel, chatMemory);
    }
}
