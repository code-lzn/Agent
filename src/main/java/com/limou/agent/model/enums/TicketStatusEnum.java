package com.limou.agent.model.enums;

import lombok.Getter;

/**
 * 电影票状态。
 *
 * @author 李振南
 */
@Getter
public enum TicketStatusEnum {

    UNUSED(0, "未使用"),
    CHECKED(1, "已核销"),
    REFUNDED(2, "已退票"),
    EXPIRED(3, "已过期");

    private final int value;
    private final String text;

    TicketStatusEnum(int value, String text) {
        this.value = value;
        this.text = text;
    }

    public static TicketStatusEnum getEnumByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (TicketStatusEnum anEnum : TicketStatusEnum.values()) {
            if (anEnum.value == value) {
                return anEnum;
            }
        }
        return null;
    }
}
