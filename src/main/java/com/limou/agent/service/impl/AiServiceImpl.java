package com.limou.agent.service.impl;

import com.limou.agent.ai.AiCodeGeneratorFactory;
import com.limou.agent.service.AiService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Optional;

@Service
public class AiServiceImpl implements AiService {

    @Resource
    private AiCodeGeneratorFactory aiCodeGeneratorFactory;

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
}
