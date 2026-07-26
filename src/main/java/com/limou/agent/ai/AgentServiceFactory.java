package com.limou.agent.ai;

import com.limou.agent.ai.tools.ToolManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AgentServiceFactory {
    @Resource
    private ChatClient chatClient;
    @Resource
    private ToolManager toolManager;

    /**
     * AI 基础对话（支持多轮对话记忆）
     */
    public String doChat(String message) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .tools(toolManager.getAllTools())
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }
}
