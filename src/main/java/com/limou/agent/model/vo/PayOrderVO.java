package com.limou.agent.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 支付视图对象。
 *
 * @author 李振南
 */
@Data
@AllArgsConstructor
public class PayOrderVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 支付宝支付页面HTML（自动提交表单）
     */
    private String payForm;

    /**
     * 订单号
     */
    private String orderNo;
}
