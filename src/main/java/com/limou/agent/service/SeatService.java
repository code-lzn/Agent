package com.limou.agent.service;

import com.limou.agent.model.vo.SeatMapVO;
import com.mybatisflex.core.service.IService;
import com.limou.agent.model.entity.Seat;

/**
 * 座位 服务层。
 *
 * @author 李振南
 */
public interface SeatService extends IService<Seat> {

    /**
     * 获取场次座位图。
     *
     * @param scheduleId 场次ID
     * @return 座位图
     */
    SeatMapVO getSeatMap(Long scheduleId);
}
