package com.limou.agent.controller;

import com.limou.agent.ai.ratelimiter.annotation.RateLimit;
import com.limou.agent.ai.ratelimiter.enums.RateLimitType;
import com.limou.agent.model.dto.movie.MovieChatRequest;
import com.limou.agent.ai.movie.MovieStateManager;
import com.limou.agent.ai.movie.tools.LockSeatsTool;
import com.limou.agent.service.AiService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 电影票智能体控制器
 * 提供电影购票对话的 REST API，复用 AiService 基础设施
 */
@Slf4j
@RestController
@RequestMapping("/movie-agent")
public class MovieAgentController {

    @Resource
    private AiService aiService;

    @Resource
    private MovieStateManager movieStateManager;

    @Resource
    private LockSeatsTool lockSeatsTool;

    /**
     * 电影票 Agent 对话（POST，支持 JSON 请求体）
     */
    @PostMapping("/chat")
    @RateLimit(limitType = RateLimitType.USER, message = "请求过于频繁，请稍后再试", rate = 5, rateInterval = 1)
    public String doChat(@RequestBody MovieChatRequest request) {
        log.info("MovieAgent POST: conversationId={}, userId={}",
                request.getConversationId(), request.getUserId());
        return aiService.doMovieChat(
                request.getMessage(),
                request.getConversationId(),
                request.getUserId()
        );
    }

    /**
     * 电影票 Agent 对话（GET，方便测试）
     */
//    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    @RateLimit(limitType = RateLimitType.USER, message = "请求过于频繁，请稍后再试", rate = 5, rateInterval = 1)
//    public String doChatGet(
//            @RequestParam String message,
//            @RequestParam String conversationId,
//            @RequestParam(required = false) Long userId) {
//        log.info("MovieAgent GET: conversationId={}, userId={}", conversationId, userId);
//        return aiService.doMovieChat(message, conversationId, userId);
//    }

    /**
     * 电影票 Agent 流式对话（SSE）
     */
    @GetMapping(value = "/chat-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> doChatStream(
            @RequestParam String message,
            @RequestParam String conversationId,
            @RequestParam(required = false) Long userId) {
        return aiService.doMovieChatStream(message, conversationId, userId);
    }

    /**
     * 重置会话（开始新对话）
     */
    @PostMapping("/reset")
    public String resetConversation(@RequestParam String conversationId) {
        movieStateManager.clearState(conversationId);
        lockSeatsTool.releaseStaleLocks(); // 释放过期座位锁
        aiService.doAgentChat("重置对话", conversationId);
        log.info("MovieAgent 重置: conversationId={}", conversationId);
        return "{\"success\":true,\"message\":\"会话已重置，座位锁已清理\"}";
    }
}
