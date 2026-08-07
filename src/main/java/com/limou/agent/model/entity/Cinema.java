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
@Table("cinema")
public class Cinema implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @Id(keyType = KeyType.Generator,value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 影院名称
     */
    private String name;

    /**
     * 详细地址
     */
    private String address;

    /**
     * 城市
     */
    private String city;

    /**
     * 经度（高德坐标系）
     */
    private BigDecimal longitude;

    /**
     * 纬度（高德坐标系）
     */
    private BigDecimal latitude;

    /**
     * 联系电话
     */
    private String phone;

    /**
     * 营业时间: 09:00-23:00
     */
    @Column("businessHours")
    private String businessHours;

    /**
     * 特色标签，逗号分隔
     */
    private String tags;

    /**
     * 基准票价（元）
     */
    @Column("basePrice")
    private BigDecimal basePrice;

    /**
     * 状态: draft/published/offline
     */
    private String status;

    @Column(value = "isDelete", isLogicDelete = true)
    private Boolean isDelete;

    @Column("createTime")
    private LocalDateTime createTime;

    @Column("updateTime")
    private LocalDateTime updateTime;

    /**
     * 距离（米），非持久化字段，后端通过高德API计算后填充，与AI选影院结果一致
     */
    @Column(ignore = true)
    private Integer distance;

}
