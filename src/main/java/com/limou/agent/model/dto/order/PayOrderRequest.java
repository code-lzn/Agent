package com.limou.agent.model.dto.order;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 支付订单请求。
 *
 * @author 李振南
 */
@Data
public class PayOrderRequest implements Serializable {

    /**
     * 订单ID
     */
    private Long orderId;

    @Serial
    private static final long serialVersionUID = 1L;
}
