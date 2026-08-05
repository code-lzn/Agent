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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        // 布局信息（从影厅 seatTemplate 带出，前端按物理格遍历渲染）
        if (cn.hutool.core.util.StrUtil.isNotBlank(hall.getSeatTemplate())) {
            try {
                cn.hutool.json.JSONObject tmpl = new cn.hutool.json.JSONObject(hall.getSeatTemplate());
                if (tmpl.containsKey("rowOverrides")) {
                    cn.hutool.json.JSONObject overrides = tmpl.getJSONObject("rowOverrides");
                    Map<Integer, Integer> rowOverrides = new HashMap<>();
                    for (Map.Entry<String, Object> entry : overrides.entrySet()) {
                        try {
                            rowOverrides.put(Integer.parseInt(entry.getKey()), ((Number) entry.getValue()).intValue());
                        } catch (Exception ignored) {
                        }
                    }
                    if (!rowOverrides.isEmpty()) vo.setRowOverrides(rowOverrides);
                }
                if (tmpl.containsKey("aisleRows")) {
                    List<Integer> aisleRows = new ArrayList<>();
                    for (Object r : tmpl.getJSONArray("aisleRows")) {
                        aisleRows.add(((Number) r).intValue());
                    }
                    vo.setAisleRows(aisleRows);
                }
                if (tmpl.containsKey("aisleCols")) {
                    List<Integer> aisleCols = new ArrayList<>();
                    for (Object c : tmpl.getJSONArray("aisleCols")) {
                        aisleCols.add(((Number) c).intValue());
                    }
                    vo.setAisleCols(aisleCols);
                }
            } catch (Exception ignored) {
            }
        }

        return vo;
    }
}
