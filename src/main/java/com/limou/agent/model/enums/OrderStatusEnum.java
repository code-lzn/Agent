package com.limou.agent.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 订单状态枚举
 *
 * @author 李振南
 */
@Getter
public enum OrderStatusEnum {

    PENDING("pending", "待支付"),
    PAID("paid", "已支付"),
    CANCELLED("cancelled", "已取消"),
    REFUNDED("refunded", "已退款"),
    COMPLETED("completed", "已完成");

    private final String value;
    private final String text;

    OrderStatusEnum(String value, String text) {
        this.value = value;
        this.text = text;
    }

    public static OrderStatusEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (OrderStatusEnum anEnum : OrderStatusEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}
