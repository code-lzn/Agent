package com.limou.agent.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 订单取消原因枚举
 *
 * @author 李振南
 */
@Getter
public enum CancelReasonEnum {

    TIMEOUT("timeout", "超时取消"),
    USER_CANCELLED("user_cancelled", "用户取消");

    private final String value;
    private final String text;

    CancelReasonEnum(String value, String text) {
        this.value = value;
        this.text = text;
    }

    public static CancelReasonEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (CancelReasonEnum anEnum : CancelReasonEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}
