package com.limou.agent.model.dto.cinema;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 影院筛选请求。
 *
 * @author 李振南
 */
@Data
public class CinemaFilterRequest implements Serializable {

    /**
     * 影院名称关键词（模糊搜索），如 "万达"
     */
    private String keyword;

    /**
     * 品牌名称（影院名称模糊匹配），如 "万达影城"
     */
    private String brand;

    /**
     * 区域名称（地址模糊匹配），如 "洛龙区"
     */
    private String district;

    /**
     * 服务标签（逗号分隔），如 "退票,可停车"
     */
    private String services;

    /**
     * 排序类型: composite(综合) / nearest(距离最近) / price(价格最低)
     */
    private String sortType;

    /**
     * 用户纬度（距离排序时需要）
     */
    private BigDecimal userLat;

    /**
     * 用户经度（距离排序时需要）
     */
    private BigDecimal userLng;

    @Serial
    private static final long serialVersionUID = 1L;
}
