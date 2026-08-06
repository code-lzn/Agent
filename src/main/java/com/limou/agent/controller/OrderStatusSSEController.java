package com.limou.agent.controller;

import com.limou.agent.mq.OrderStatusNotifier;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 订单状态 SSE 推送端点
 * <p>
 * 前端通过 EventSource 订阅此端点，接收订单状态变更的实时推送。
 * 使用示例：
 * <pre>
 *   const es = new EventSource('/api/sse/order/{userId}');
 *   es.addEventListener('order_cancelled', e => { ... });
 *   es.addEventListener('order_paid', e => { ... });
 * </pre>
 */
@RestController
@RequestMapping("/sse")
public class OrderStatusSSEController {

    @Resource
    private OrderStatusNotifier orderStatusNotifier;

    /**
     * 订阅当前用户的订单状态变更
     */
    @GetMapping("/order/{userId}")
    public SseEmitter subscribe(@PathVariable Long userId) {
        return orderStatusNotifier.register(userId);
    }
}