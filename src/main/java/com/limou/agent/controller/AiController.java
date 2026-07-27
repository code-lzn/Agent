package com.limou.agent.controller;

import com.limou.agent.ai.ratelimiter.annotation.RateLimit;
import com.limou.agent.ai.ratelimiter.enums.RateLimitType;
import com.limou.agent.service.AiService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private AiService aiService;

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(limitType = RateLimitType.USER, message = "请求过于频繁，请稍后再试", rate = 5, rateInterval = 1)
    public String doChat(String message, String conversationId) {
        return aiService.doChat(message, conversationId);
    }

    @GetMapping(value = "/chat-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatStream(String message, String conversationId) {
        return aiService.doChatStream(message, conversationId);
    }
}
