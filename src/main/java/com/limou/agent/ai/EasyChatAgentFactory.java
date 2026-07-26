package com.limou.agent.ai;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.tool.ToolCallback;
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
    private  final DashScopeChatModel dashScopeChatModel;
//    private final ChatModel chatModel;
    private final ToolCallback[] tools;

    @Bean
    public ChatMemory deepSeekChatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(10)
                .build();
    }

    @Bean
    public ChatMemory dashScopeChatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(10)
                .build();
    }

    // 2. 抽取私有工厂方法
    private ChatClient buildChatClient(ChatModel model, ChatMemory chatMemory) {
        return ChatClient.builder(model)
                .defaultSystem(systemPrompt)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory)
                                .build()
                )
                .defaultToolCallbacks(tools)
                .build();
    }

    // 3. 分别暴露两个 Bean，并指定明确名称（默认方法名即可）
    @Bean
    public ChatClient deepSeekChatClient(DeepSeekChatModel deepSeekModel,
                                         @Qualifier("deepSeekChatMemory") ChatMemory deepSeekMemory) {
        return buildChatClient(deepseekChatModel, deepSeekMemory);
    }

    @Bean
    public ChatClient dashScopeChatClient(DashScopeChatModel dashScopeModel,
                                          @Qualifier("dashScopeChatMemory") ChatMemory dashScopeMemory) {
        return buildChatClient(dashScopeChatModel, dashScopeMemory);
    }
}
