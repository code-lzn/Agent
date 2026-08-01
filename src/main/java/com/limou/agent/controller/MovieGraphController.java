package com.limou.agent.controller;

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
 * 电影票 StateGraph 工作流控制器
 *
 * 与 ReAct 模式（MovieAgentController）的区别:
 * - ReAct: 全量工具丢给 LLM，LLM 自主选择调用
 * - Graph: 代码控流程，LLM 只做意图识别和回复生成
 *
 * 端点前缀: /movie-graph
 */
@Slf4j
@RestController
@RequestMapping("/movie-graph")
public class MovieGraphController {

    @Resource
    private AiService aiService;

    @Resource
    private MovieStateManager movieStateManager;

    @Resource
    private LockSeatsTool lockSeatsTool;

    /**
     * Graph 工作流流式对话（SSE）
     * 流程: GuardRail → 意图识别(LLM) → 工具路由(代码) → 工具执行 → 回复生成(LLM流式)
     */
    @GetMapping(value = "/chat-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> doChatStream(
            @RequestParam String message,
            @RequestParam String conversationId,
            @RequestParam(required = false) Long userId) {
        log.info("GraphWorkflow SSE: conversationId={}, userId={}", conversationId, userId);
        return aiService.doMovieGraphChatStream(message, conversationId, userId);
    }

    /**
     * 重置会话
     */
    @PostMapping("/reset")
    public String resetConversation(@RequestParam String conversationId) {
        movieStateManager.clearState(conversationId);
        lockSeatsTool.releaseStaleLocks();
        log.info("GraphWorkflow 重置: conversationId={}", conversationId);
        return "{\"success\":true,\"message\":\"会话已重置，座位锁已清理\"}";
    }
}