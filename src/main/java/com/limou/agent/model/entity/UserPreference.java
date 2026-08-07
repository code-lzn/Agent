package com.limou.agent.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.io.Serializable;
import java.math.BigDecimal;
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
@Table("user_preference")
public class UserPreference implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @Id(keyType = KeyType.Generator,value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 用户ID
     */
    @Column("userId")
    private Long userId;

    /**
     * 偏好影片类型，逗号分隔
     */
    @Column("preferredTypes")
    private String preferredTypes;

    /**
     * 偏好厅型: IMAX/杜比/普通/4DX/VIP
     */
    @Column("preferredHallType")
    private String preferredHallType;

    /**
     * 单张票价预算上限（元）
     */
    @Column("budgetMax")
    private BigDecimal budgetMax;

    /**
     * 常去影院ID
     */
    @Column("frequentCinemaId")
    private Long frequentCinemaId;

    /**
     * 常用座位区域: 中间/靠前/靠后/靠边
     */
    @Column("preferredSeatZone")
    private String preferredSeatZone;

    @Column(value = "isDelete", isLogicDelete = true)
    private Boolean isDelete;

    @Column("createTime")
    private LocalDateTime createTime;

    @Column("updateTime")
    private LocalDateTime updateTime;

}
