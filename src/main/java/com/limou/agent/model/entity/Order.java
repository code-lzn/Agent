package com.limou.agent.model.entity;


import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 订单
 * @TableName order
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("order")
public class Order implements Serializable {
    /**
     * 主键ID
     */
    @Id(keyType = KeyType.Generator,value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 订单编号（唯一）
     */
    private String orderNo;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 场次ID
     */
    private Long scheduleId;

    /**
     * 影片名称（冗余）
     */
    private String filmName;

    /**
     * 影院名称（冗余）
     */
    private String cinemaName;

    /**
     * 放映时间（冗余）
     */
    private String scheduleTime;

    /**
     * 影厅名称（冗余）
     */
    private String hallname;

    /**
     * 订单总价（元）
     */
    private BigDecimal totalPrice;

    /**
     * 购票数量
     */
    private Integer count;

    /**
     * 状态: pending/paid/cancelled/completed
     */
    private String status;

    /**
     * 取消原因: timeout/user_cancelled
     */
    private String cancelReason;

    /**
     * 支付宝交易号（沙箱生成）
     */
    private String alipayTradeNo;

    /**
     * 支付宝状态
     */
    private String alipayStatus;

    /**
     * 实际支付时间
     */
    private Date paidAt;

    /**
     * 超时截止时间（创建时间+15分钟）
     */
    private Date expireAt;

    /**
     * 
     */
    private Integer isDelete;

    /**
     * 
     */
    private Date createTime;

    /**
     * 
     */
    private Date updateTime;

    @Column(value = "isDelete", isLogicDelete = true)
    private static final long serialVersionUID = 1L;
}