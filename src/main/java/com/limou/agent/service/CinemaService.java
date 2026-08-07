package com.limou.agent.service;

import com.mybatisflex.core.service.IService;
import com.limou.agent.model.entity.Cinema;
import com.limou.agent.model.dto.cinema.CinemaFilterRequest;

import java.math.BigDecimal;
import java.util.List;

/**
 *  服务层。
 *
 * @author 李振南
 */
public interface CinemaService extends IService<Cinema> {

    /**
     * 多条件筛选影院。
     *
     * @param request 筛选条件
     * @return 符合条件的影院列表
     */
    List<Cinema> filterCinemas(CinemaFilterRequest request);

    /**
     * 通过高德 API 计算影院到用户位置的直线距离（米），结果写入 Cinema.distance。
     * 与 AI 选影院工具使用同一套高德算法，确保距离一致。
     *
     * @param cinema   影院实体（会被原地修改 distance 字段）
     * @param userLat  用户纬度
     * @param userLng  用户经度
     */
    void computeAmapDistance(Cinema cinema, BigDecimal userLat, BigDecimal userLng);
}
