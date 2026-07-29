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
@Table("hall")
public class Hall implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @Id(keyType = KeyType.Generator,value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 所属影院ID
     */
    @Column("cinemaId")
    private Long cinemaId;

    /**
     * 影厅名称
     */
    private String name;

    /**
     * 厅型: IMAX/杜比/普通/4DX/VIP
     */
    @Column("hallType")
    private String hallType;

    /**
     * 座位行数
     */
    @Column("rowCount")
    private Integer rowCount;

    /**
     * 座位列数
     */
    @Column("colCount")
    private Integer colCount;

    /**
     * 座位模板JSON（特殊座位标记等）
     */
    @Column("seatTemplate")
    private String seatTemplate;

    @Column(value = "isDelete", isLogicDelete = true)
    private Boolean isDelete;

    @Column("createTime")
    private LocalDateTime createTime;

    @Column("updateTime")
    private LocalDateTime updateTime;

}
