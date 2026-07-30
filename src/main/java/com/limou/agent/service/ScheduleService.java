package com.limou.agent.service;

import com.limou.agent.model.dto.schedule.ConflictCheckRequest;
import com.limou.agent.model.entity.Schedule;
import com.limou.agent.model.vo.ScheduleVO;
import com.mybatisflex.core.service.IService;

import java.sql.Date;
import java.util.List;

/**
 * 排期 服务层。
 *
 * @author 李振南
 */
public interface ScheduleService extends IService<Schedule> {

    /**
     * 查询排期列表（按影院分组，含关联名称）。
     */
    List<ScheduleVO> queryScheduleList(Long filmId, Long cinemaId, Date showDate);

    /**
     * 排期冲突校验。
     *
     * @param request 冲突校验请求
     * @return true 有冲突, false 无冲突
     */
    boolean checkConflict(ConflictCheckRequest request);

    /**
     * 保存排期并自动初始化座位。
     *
     * @param schedule 排期信息
     * @return 排期ID
     */
    Long saveScheduleWithSeats(Schedule schedule);
}
