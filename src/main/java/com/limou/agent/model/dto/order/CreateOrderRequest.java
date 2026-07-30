package com.limou.agent.model.dto.order;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 创建订单请求。
 *
 * @author 李振南
 */
@Data
public class CreateOrderRequest implements Serializable {

    /**
     * 场次ID
     */
    private Long scheduleId;

    /**
     * 座位ID列表
     */
    private List<Long> seatIds;

    @Serial
    private static final long serialVersionUID = 1L;
}
