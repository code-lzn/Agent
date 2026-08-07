package com.limou.agent.service;

import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.Optional;

public interface AiService {
    /**
     * 电影票 Agent 智能路由流式对话（SSE）
     *
     * @param message        用户输入
     * @param conversationId 会话ID
     * @param userId         用户ID（用于偏好加载）
     * @param currentCity    当前城市（GPS/IP 定位）
     * @param lat            用户纬度
     * @param lng            用户经度
     * @return SSE 流
     */
    Flux<ServerSentEvent<String>> doMovieSmartChatStream(
            String message, String conversationId, Long userId, String currentCity,
            Double lat, Double lng);
}
