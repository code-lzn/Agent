package com.limou.agent.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.time.LocalDateTime;

import java.io.Serial;

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
@Table("schedule")
public class Schedule implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 影片ID
     */
    @Column("filmId")
    private Long filmId;

    /**
     * 影院ID
     */
    @Column("cinemaId")
    private Long cinemaId;

    /**
     * 影厅ID
     */
    @Column("hallId")
    private Long hallId;

    /**
     * 放映日期
     */
    @Column("showDate")
    private Date showDate;

    /**
     * 开场时间
     */
    @Column("startTime")
    private Time startTime;

    /**
     * 散场时间（自动计算: startTime + 片长 + 15min）
     */
    @Column("endTime")
    private Time endTime;

    /**
     * 标准票价（元）
     */
    private BigDecimal price;

    /**
     * VIP区票价（元）
     */
    @Column("vipPrice")
    private BigDecimal vipPrice;

    /**
     * 状态: draft/published/offline/soldOut
     */
    private String status;

    @Column(value = "isDelete", isLogicDelete = true)
    private Boolean isDelete;

    @Column("createTime")
    private LocalDateTime createTime;

    @Column("updateTime")
    private LocalDateTime updateTime;

}
