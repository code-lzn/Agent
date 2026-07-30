package com.limou.agent.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.limou.agent.exception.BusinessException;
import com.limou.agent.exception.ErrorCode;
import com.limou.agent.model.entity.Hall;
import com.limou.agent.model.entity.Schedule;
import com.limou.agent.model.vo.SeatMapVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.limou.agent.model.entity.Seat;
import com.limou.agent.mapper.SeatMapper;
import com.limou.agent.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 座位 服务层实现。
 *
 * @author 李振南
 */
@Service
public class SeatServiceImpl extends ServiceImpl<SeatMapper, Seat> implements SeatService {

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private HallService hallService;

    @Override
    public SeatMapVO getSeatMap(Long scheduleId) {
        if (scheduleId == null || scheduleId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "场次ID无效");
        }

        // 1. 查询场次信息
        Schedule schedule = scheduleService.getById(scheduleId);
        if (schedule == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "场次不存在");
        }

        // 2. 查询影厅信息
        Hall hall = hallService.getById(schedule.getHallId());
        if (hall == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "影厅不存在");
        }

        // 3. 查询座位列表
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("scheduleId", scheduleId)
                .eq("isDelete", 0)
                .orderBy("rowNum", true)
                .orderBy("colNum", true);
        List<Seat> seats = mapper.selectListByQuery(queryWrapper);

        // 4. 组装
        SeatMapVO vo = new SeatMapVO();
        vo.setScheduleId(scheduleId);
        vo.setPrice(schedule.getPrice());
        vo.setVipPrice(schedule.getVipPrice());
        vo.setHallId(hall.getId());
        vo.setHallName(hall.getName());
        vo.setHallType(hall.getHallType());
        vo.setRowCount(hall.getRowCount());
        vo.setColCount(hall.getColCount());
        vo.setSeats(seats);

        return vo;
    }
}
