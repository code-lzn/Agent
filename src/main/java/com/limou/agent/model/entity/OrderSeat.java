package com.limou.agent.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.io.Serializable;
import java.time.LocalDateTime;

import java.io.Serial;

import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 *  实体类。
 *
 * @author 李振南
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("order_seat")
public class OrderSeat implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @Id(keyType = KeyType.Generator,value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 订单ID
     */
    @Column("orderId")
    private Long orderId;

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
     * 是否已使用: 0-未使用 1-已核销
     */
    @Column("isUsed")
    private Boolean isUsed;

    @Column(value = "isDelete", isLogicDelete = true)
    private Boolean isDelete;

    @Column("createTime")
    private LocalDateTime createTime;

    @Column("updateTime")
    private LocalDateTime updateTime;

}
