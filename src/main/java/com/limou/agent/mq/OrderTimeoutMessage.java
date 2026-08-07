package com.limou.agent.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 订单超时延时消息体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderTimeoutMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单 ID */
    private Long orderId;

    /** 订单编号 */
    private String orderNo;

    /** 用户 ID（用于 SSE 推送） */
    private Long userId;

    /** 场次 ID（用于释放座位） */
    private Long scheduleId;

    /** 消息发送时间戳（毫秒） */
    private long createdAt;
}