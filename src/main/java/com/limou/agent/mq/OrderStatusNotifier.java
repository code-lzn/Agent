package com.limou.agent.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订单状态变更 SSE 通知器
 * <p>
 * 前端订阅 /api/sse/order/{userId} 后，后端可通过此类推送订单状态变更事件。
 */
@Slf4j
@Component
public class OrderStatusNotifier {

    /** userId -> SseEmitter 映射 */
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * 注册 SSE 连接
     */
    public SseEmitter register(Long userId) {
        // 移除旧连接
        SseEmitter old = emitters.remove(userId);
        if (old != null) {
            try { old.complete(); } catch (Exception ignored) {}
        }

        SseEmitter emitter = new SseEmitter(0L); // 无超时
        emitters.put(userId, emitter);

        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));
        emitter.onError(e -> emitters.remove(userId));

        // 发送初始连接确认
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"status\":\"connected\"}"));
        } catch (IOException e) {
            emitters.remove(userId);
        }

        return emitter;
    }

    /**
     * 通知订单已取消
     */
    public void notifyOrderCancelled(Long userId, Long orderId, String reason) {
        notify(userId, "order_cancelled",
                String.format("{\"orderId\":%d,\"reason\":\"%s\"}", orderId, reason));
    }

    /**
     * 通知订单已支付成功
     */
    public void notifyOrderPaid(Long userId, Long orderId) {
        notify(userId, "order_paid",
                String.format("{\"orderId\":%d}", orderId));
    }

    /**
     * 通用通知
     */
    private void notify(Long userId, String eventName, String data) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException e) {
            log.warn("SSE 推送失败: userId={}, event={}", userId, eventName);
            emitters.remove(userId);
        }
    }
}