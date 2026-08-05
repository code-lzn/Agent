package com.limou.agent.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 电影票（每座位一票，独立取票码，可分次核销）。
 *
 * @author 李振南
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("ticket")
public class Ticket implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 所属订单ID
     */
    @Column("orderId")
    private Long orderId;

    /**
     * 场次ID（核销时间窗校验用）
     */
    @Column("scheduleId")
    private Long scheduleId;

    /**
     * 座位ID
     */
    @Column("seatId")
    private Long seatId;

    /**
     * 座位标签（冗余: 5排6座）
     */
    @Column("seatLabel")
    private String seatLabel;

    /**
     * 独立取票码（8位数字，唯一）
     */
    @Column("ticketCode")
    private String ticketCode;

    /**
     * 核销状态: 0-未核销 1-已核销
     */
    private Integer status;

    /**
     * 核销时间
     */
    @Column("checkedInAt")
    private LocalDateTime checkedInAt;

    /**
     * 核销人（后台管理员用户ID）
     */
    @Column("checkedBy")
    private Long checkedBy;

    @Column(value = "isDelete", isLogicDelete = true)
    private Boolean isDelete;

    @Column("createTime")
    private LocalDateTime createTime;

    @Column("updateTime")
    private LocalDateTime updateTime;
}
