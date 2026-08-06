package com.limou.agent.service;

import com.mybatisflex.core.service.IService;
import com.limou.agent.model.entity.Cinema;
import com.limou.agent.model.dto.cinema.CinemaFilterRequest;

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
}
