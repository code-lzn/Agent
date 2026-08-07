package com.limou.agent.mq;

import com.limou.agent.mapper.OrderMapper;
import com.limou.agent.mapper.OrderSeatMapper;
import com.limou.agent.model.entity.Order;
import com.limou.agent.model.entity.OrderSeat;
import com.limou.agent.model.enums.CancelReasonEnum;
import com.limou.agent.model.enums.OrderStatusEnum;
import com.limou.agent.service.SeatLockService;
import com.mybatisflex.core.query.QueryWrapper;
import com.rabbitmq.client.Channel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单超时消费者
 * <p>
 * 监听死信队列 order.timeout.dlq，收到消息说明订单已过 15 分钟未支付。
 * 使用乐观锁（status='pending'）确保只取消一次，防止重复消费。
 */
@Slf4j
@Component
public class OrderTimeoutConsumer {

    @Resource
    private OrderMapper orderMapper;

    @Resource
    private OrderSeatMapper orderSeatMapper;

    @Resource
    private SeatLockService seatLockService;

    @Resource
    private OrderStatusNotifier orderStatusNotifier;

    /**
     * 监听死信队列，处理超时订单
     */
    @RabbitListener(queues = OrderTimeoutConfig.ORDER_TIMEOUT_DLQ, ackMode = "MANUAL")
    @Transactional(rollbackFor = Exception.class)
    public void handleOrderTimeout(OrderTimeoutMessage message,
                                   Channel channel,
                                   @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            if (message == null || message.getOrderId() == null) {
                log.warn("收到空超时消息，直接 ACK");
                channel.basicAck(deliveryTag, false);
                return;
            }

            Long orderId = message.getOrderId();
            log.info("收到订单超时消息: orderId={}, orderNo={}", orderId, message.getOrderNo());

            // 乐观锁查询：只处理仍为 pending 的订单
            Order order = orderMapper.selectOneByQuery(
                    QueryWrapper.create()
                            .eq(Order::getId, orderId)
                            .eq(Order::getStatus, OrderStatusEnum.PENDING.getValue()));

            if (order == null) {
                // 订单已支付/已取消/不存在 → 直接 ACK
                log.info("订单 {} 已非 pending 状态，跳过超时处理", orderId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 乐观锁更新：仅当订单仍为 pending 时才取消（原子操作，防止重复消费）
            int updated = orderMapper.updateByQuery(
                    Order.builder()
                            .status(OrderStatusEnum.CANCELLED.getValue())
                            .cancelReason(CancelReasonEnum.TIMEOUT.getValue())
                            .build(),
                    QueryWrapper.create()
                            .eq(Order::getId, orderId)
                            .eq(Order::getStatus, OrderStatusEnum.PENDING.getValue())
            );
            if (updated == 0) {
                log.info("订单 {} 状态已变更，跳过重复取消", orderId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 释放座位
            List<OrderSeat> orderSeats = orderSeatMapper.selectListByQuery(
                    QueryWrapper.create().eq(OrderSeat::getOrderId, orderId));
            if (!orderSeats.isEmpty()) {
                List<Long> seatIds = orderSeats.stream()
                        .map(OrderSeat::getSeatId)
                        .collect(Collectors.toList());
                seatLockService.releaseSeatsToAvailable(order.getScheduleId(), seatIds);
                log.info("已释放订单 {} 的 {} 个座位", orderId, seatIds.size());
            }

            log.info("订单 {} 超时已自动取消", orderId);

            // 通过 SSE 推送通知前端
            orderStatusNotifier.notifyOrderCancelled(message.getUserId(), orderId, CancelReasonEnum.TIMEOUT.getValue());

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("处理订单超时消息失败: orderId={}", message != null ? message.getOrderId() : null, e);
            try {
                // 异常时拒绝但不重新入队（避免死循环）
                channel.basicNack(deliveryTag, false, false);
            } catch (Exception ignored) {
            }
        }
    }
}