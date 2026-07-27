package com.limou.agent.service.impl;

import com.limou.agent.ai.AgentServiceFactory;
import com.limou.agent.service.AiService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Optional;

@Service
public class AiServiceImpl implements AiService {

    @Resource
    private AgentServiceFactory agentServiceFactory;

    @Override
    public String doChat(String message, String conversationId) {
        return agentServiceFactory.doChat(message, conversationId);
    }

    @Override
    public Flux<String> doChatStream(String message, String conversationId) {
        return agentServiceFactory.doChatStream(message, conversationId);
    }

    @Override
    public <T> T doChatStructured(String message, String conversationId, Class<T> outputType) {
        return agentServiceFactory.doChatStructured(message, conversationId, outputType);
    }

    @Override
    public String doAgentChat(String message, String conversationId) {
        return agentServiceFactory.doAgentChat(message, conversationId);
    }

    @Override
    public <T> Optional<T> doAgentChatStructured(String message, String conversationId, Class<T> outputType) {
        return agentServiceFactory.doAgentChatStructured(message, conversationId, outputType);
    }
}
