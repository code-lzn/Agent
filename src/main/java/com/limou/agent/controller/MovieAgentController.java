package com.limou.agent.controller;

import com.limou.agent.ai.ratelimiter.annotation.RateLimit;
import com.limou.agent.ai.ratelimiter.enums.RateLimitType;
import com.limou.agent.model.dto.movie.ConversationState;
import com.limou.agent.model.dto.movie.MovieChatRequest;
import com.limou.agent.ai.movie.MovieStateManager;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.limou.agent.ai.movie.tools.LockSeatsTool;
import com.limou.agent.ai.movie.tools.PayOrderTool;
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

    @Resource
    private PayOrderTool payOrderTool;

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
     * 电影票 Agent 流式对话（SSE）—— ReAct 模式，LLM 自主调用工具
     */
    @GetMapping(value = "/chat-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> doChatStream(
            @RequestParam String message,
            @RequestParam String conversationId,
            @RequestParam(required = false) Long userId) {
        return aiService.doMovieChatStream(message, conversationId, userId);
    }

    /**
     * 电影票 Agent 智能路由流式对话（SSE）
     * 自动判断：信息齐全 → ReAct 一句完成，信息不足 → Graph 逐步引导
     */
    @GetMapping(value = "/smart-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> doSmartStream(
            @RequestParam String message,
            @RequestParam String conversationId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        return aiService.doMovieSmartChatStream(message, conversationId, userId, city, lat, lng);
    }

    /**
     * 支付宝支付页面（独立接口，不经过 SSE，避免大段 HTML 在流中传输异常）
     */
    @GetMapping(value = "/pay-form", produces = MediaType.TEXT_HTML_VALUE)
    public String payForm(@RequestParam Long orderId) {
        log.info("PayForm 请求: orderId={}", orderId);
        String resultJson = payOrderTool.payOrder(orderId, "alipay");
        JSONObject json = JSONUtil.parseObj(resultJson);
        if (json.getBool("success", false)) {
            return json.getStr("payForm");
        }
        String error = json.getStr("error", json.getStr("message", "支付异常"));
        return "<html><body style=\"display:flex;align-items:center;justify-content:center;height:100vh;font-family:sans-serif;\"><div style=\"text-align:center\"><h2>支付失败</h2><p>" + error + "</p></div></body></html>";
    }

    /**
     * 同步座位页操作到 AI 状态 —— 用户在选座页手动下单后，让 AI 感知上下文
     */
    @PostMapping("/sync-state")
    public String syncState(@RequestParam Long userId,
                            @RequestParam Long scheduleId,
                            @RequestParam Long orderId,
                            @RequestParam(required = false) String seatLabels) {
        // 查找该用户当前会话
        String conversationId = movieStateManager.findCurrentConversationId(userId);
        if (conversationId == null) {
            return "{\"success\":false,\"message\":\"无活跃会话\"}";
        }
        ConversationState state = movieStateManager.getState(conversationId);
        state.setScheduleId(scheduleId);
        state.setOrderId(orderId);
        if (seatLabels != null && !seatLabels.isBlank()) {
            state.setSeatLabels(java.util.Arrays.asList(seatLabels.split("、")));
        }
        state.setUserId(userId);
        movieStateManager.saveState(conversationId, state);
        log.info("SyncState: conversationId={}, scheduleId={}, orderId={}, seats={}",
                conversationId, scheduleId, orderId, seatLabels);
        return "{\"success\":true,\"message\":\"状态已同步\"}";
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
