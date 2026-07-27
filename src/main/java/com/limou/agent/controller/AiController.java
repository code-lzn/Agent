package com.limou.agent.controller;

import com.limou.agent.ai.AgentServiceFactory;
import com.limou.agent.ai.ratelimiter.annotation.RateLimit;
import com.limou.agent.ai.ratelimiter.enums.RateLimitType;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai")
public class AiController {
    @Resource
    private AgentServiceFactory agentServiceFactory;
    @GetMapping("/chat")
    @RateLimit(limitType = RateLimitType.USER, message = "请求过于频繁，请稍后再试", rate = 5, rateInterval = 1)
    public String doChat(String message, String conversationId) {
        return agentServiceFactory.doChat(message, conversationId);
    }

}
