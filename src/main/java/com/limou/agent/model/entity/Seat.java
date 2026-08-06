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
@Table("seat")
public class Seat implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @Id(keyType = KeyType.Generator,value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 场次ID
     */
    @Column("scheduleId")
    private Long scheduleId;

    /**
     * 影厅ID
     */
    @Column("hallId")
    private Long hallId;

    /**
     * 行号（从1开始）
     */
    @Column("rowNum")
    private Integer rowNum;

    /**
     * 列号（从1开始）
     */
    @Column("colNum")
    private Integer colNum;

    /**
     * 座位标签: 5排6座
     */
    @Column("seatLabel")
    private String seatLabel;

    /**
     * 区域: vip/regular
     */
    private String zone;

    /**
     * 状态: available/locked/sold
     */
    private String status;

    @Column(value = "isDelete", isLogicDelete = true)
    private Boolean isDelete;

    @Column("createTime")
    private LocalDateTime createTime;

    @Column("updateTime")
    private LocalDateTime updateTime;

}
