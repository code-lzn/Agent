package com.limou.agent.ai;

import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;

@Configuration
@RequiredArgsConstructor
public class AiConfig {

    @Value("classpath:prompts/system-prompt.st")
    private Resource systemPrompt;

    private final DeepSeekChatModel deepseekChatModel;

    @Bean
    ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(10)
                .build();
    }

    @Bean
    @Primary
    ChatClient chatClient(ChatMemory chatMemory) {

        ChatClient chatClient = ChatClient.builder(deepseekChatModel)
                .defaultSystem(systemPrompt)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                        // 自定义日志 Advisor，可按需开启
//                        // 自定义推理增强 Advisor，可按需开启
//                       ,new ReReadingAdvisor()
                )
                .build();
        return chatClient;
//        return builder
//                .defaultSystem(systemPrompt)
//                .defaultAdvisors(
//                        MessageChatMemoryAdvisor.builder(chatMemory).build()
//                )
//                .build();
    }
}
