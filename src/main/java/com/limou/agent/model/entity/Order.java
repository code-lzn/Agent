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

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单
 * @TableName `order`
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("order")
public class Order implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 订单编号（唯一）
     */
    @Column("orderNo")
    private String orderNo;

    /**
     * 用户ID
     */
    @Column("userId")
    private Long userId;

    /**
     * 场次ID
     */
    @Column("scheduleId")
    private Long scheduleId;

    /**
     * 影片名称（冗余）
     */
    @Column("filmName")
    private String filmName;

    /**
     * 影院名称（冗余）
     */
    @Column("cinemaName")
    private String cinemaName;

    /**
     * 放映时间（冗余）
     */
    @Column("scheduleTime")
    private String scheduleTime;

    /**
     * 影厅名称（冗余）
     */
    @Column("hallName")
    private String hallName;

    /**
     * 订单总价（元）
     */
    @Column("totalPrice")
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
    @Column("cancelReason")
    private String cancelReason;

    /**
     * 退款金额（元）
     */
    @Column("refundAmount")
    private BigDecimal refundAmount;

    /**
     * 支付宝退款交易号
     */
    @Column("refundTradeNo")
    private String refundTradeNo;

    /**
     * 退款时间
     */
    @Column("refundTime")
    private LocalDateTime refundTime;

    /**
     * 支付宝交易号（沙箱生成）
     */
    @Column("alipayTradeNo")
    private String alipayTradeNo;

    /**
     * 支付宝状态
     */
    @Column("alipayStatus")
    private String alipayStatus;

    /**
     * 实际支付时间
     */
    @Column("paidAt")
    private LocalDateTime paidAt;

    /**
     * 超时截止时间（创建时间+15分钟）
     */
    @Column("expireAt")
    private LocalDateTime expireAt;

    @Column(value = "isDelete", isLogicDelete = true)
    private Boolean isDelete;

    @Column("createTime")
    private LocalDateTime createTime;

    @Column("updateTime")
    private LocalDateTime updateTime;

    /**
     * 是否有已核销的票（非数据库字段，管理端列表填充用，控制退款入口显示）
     */
    @Column(ignore = true)
    private Boolean hasCheckedTicket;
}
