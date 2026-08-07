package com.limou.agent.model.entity;

import com.limou.agent.model.enums.FilmStatusEnum;
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
import java.sql.Date;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 实体类。
 *
 * @author 李振南
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("film")
public class Film implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 影片名称
     */
    private String name;

    /**
     * 英文名称
     */
    @Column("englishName")
    private String englishName;

    /**
     * 影片类型，逗号分隔
     */
    private String type;

    /**
     * 评分 1.0-10.0
     */
    private BigDecimal rating;

    /**
     * 片长（分钟）
     */
    private Integer duration;

    /**
     * 海报图片地址
     */
    @Column("posterUrl")
    private String posterUrl;

    /**
     * 上映日期
     */
    @Column("releaseDate")
    private Date releaseDate;

    /**
     * 导演
     */
    private String director;

    /**
     * 主演，逗号分隔
     */
    private String actors;

    /**
     * 影片简介（最多500字）
     */
    private String description;

    /**
     * 状态: draft/published/offline
     */
    private String status;

    /**
     * 制式标签（IMAX/杜比/2D等），从排片+影厅汇总，非持久化字段
     */
    @Column(ignore = true)
    private List<String> formatTags;

    @Column(value = "isDelete", isLogicDelete = true)
    private Boolean isDelete;

    @Column("createTime")
    private LocalDateTime createTime;

    @Column("updateTime")
    private LocalDateTime updateTime;

}
