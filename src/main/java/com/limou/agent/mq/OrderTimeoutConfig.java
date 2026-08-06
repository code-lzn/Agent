package com.limou.agent.mq;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 订单超时延时消息 RabbitMQ 配置（TTL + DLX 模式）
 * <p>
 * 流程：
 * 1. 创建订单 → 发送消息到 order.timeout.queue（TTL=15分钟）
 * 2. 15分钟后消息过期 → 自动路由到 order.timeout.dlx → order.timeout.dlq
 * 3. 消费者监听 order.timeout.dlq → 检查订单状态 → 仍为 pending 则取消
 * 4. 若用户在 15 分钟内支付成功 → 消费者发现订单已 paid → 直接 ACK 跳过
 *
 * @author agent-film
 */
@Configuration
public class OrderTimeoutConfig {

    /** 延时队列使用的交换机 */
    public static final String ORDER_TIMEOUT_EXCHANGE = "order.timeout.exchange";
    /** 延时队列（带 TTL） */
    public static final String ORDER_TIMEOUT_QUEUE = "order.timeout.queue";
    /** 死信交换机 */
    public static final String ORDER_TIMEOUT_DLX = "order.timeout.dlx";
    /** 死信队列（消费者实际监听） */
    public static final String ORDER_TIMEOUT_DLQ = "order.timeout.dlq";
    /** 路由键 */
    public static final String ORDER_TIMEOUT_ROUTING_KEY = "order.timeout";

    /** 订单超时时间（毫秒），默认 15 分钟 */
//    public static final int ORDER_TIMEOUT_MS = 15 * 60 * 1000;
            //测试
        public static final int ORDER_TIMEOUT_MS = 2*60*1000;

    // ==================== 延时队列交换机 ====================

    @Bean
    public DirectExchange orderTimeoutExchange() {
        return new DirectExchange(ORDER_TIMEOUT_EXCHANGE, true, false);
    }

    // ==================== 延时队列（带 TTL，绑定死信交换机） ====================

    @Bean
    public Queue orderTimeoutQueue() {
        return QueueBuilder.durable(ORDER_TIMEOUT_QUEUE)
                .withArgument("x-dead-letter-exchange", ORDER_TIMEOUT_DLX)
                .withArgument("x-dead-letter-routing-key", ORDER_TIMEOUT_ROUTING_KEY)
                .withArgument("x-message-ttl", ORDER_TIMEOUT_MS)
                .build();
    }

    @Bean
    public Binding orderTimeoutBinding() {
        return BindingBuilder.bind(orderTimeoutQueue())
                .to(orderTimeoutExchange())
                .with(ORDER_TIMEOUT_ROUTING_KEY);
    }

    // ==================== 死信交换机 ====================

    @Bean
    public DirectExchange orderTimeoutDlx() {
        return new DirectExchange(ORDER_TIMEOUT_DLX, true, false);
    }

    // ==================== 死信队列（消费者监听） ====================

    @Bean
    public Queue orderTimeoutDlq() {
        return QueueBuilder.durable(ORDER_TIMEOUT_DLQ).build();
    }

    @Bean
    public Binding orderTimeoutDlqBinding() {
        return BindingBuilder.bind(orderTimeoutDlq())
                .to(orderTimeoutDlx())
                .with(ORDER_TIMEOUT_ROUTING_KEY);
    }

    // ==================== JSON 消息转换器 ====================

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}