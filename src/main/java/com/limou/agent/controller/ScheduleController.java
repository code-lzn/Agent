package com.limou.agent.controller;

import com.limou.agent.common.BaseResponse;
import com.limou.agent.common.ResultUtils;
import com.limou.agent.exception.ErrorCode;
import com.limou.agent.exception.ThrowUtils;
import com.limou.agent.model.dto.schedule.ConflictCheckRequest;
import com.limou.agent.model.vo.ScheduleVO;
import cn.hutool.core.collection.CollUtil;
import com.mybatisflex.core.paginate.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.limou.agent.model.entity.Schedule;
import com.limou.agent.service.ScheduleService;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Date;
import java.util.List;

/**
 * 排期 控制层。
 *
 * @author 李振南
 */
@RestController
@RequestMapping("/schedule")
public class ScheduleController {

    @Autowired
    private ScheduleService scheduleService;

    // ========== 前台接口 ==========

    /**
     * 排期列表（按影院分组，含关联信息）。
     */
    @GetMapping("/list")
    public BaseResponse<List<ScheduleVO>> listSchedule(
            @RequestParam(required = false) Long filmId,
            @RequestParam(required = false) Long cinemaId,
            @RequestParam(required = false) Date showDate) {
        ThrowUtils.throwIf(filmId == null && cinemaId == null,
                ErrorCode.PARAMS_ERROR, "影片ID和影院ID不能同时为空");
        List<ScheduleVO> list = scheduleService.queryScheduleList(filmId, cinemaId, showDate);
        return ResultUtils.success(list);
    }

    // ========== 后台管理接口 ==========

    /**
     * 保存排期（含自动初始化座位）。
     */
    @PostMapping("save")
    public BaseResponse<Long> save(@RequestBody Schedule schedule) {
        ThrowUtils.throwIf(schedule == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(schedule.getFilmId() == null, ErrorCode.PARAMS_ERROR, "影片不能为空");
        ThrowUtils.throwIf(schedule.getCinemaId() == null, ErrorCode.PARAMS_ERROR, "影院不能为空");
        ThrowUtils.throwIf(schedule.getHallId() == null, ErrorCode.PARAMS_ERROR, "影厅不能为空");
        ThrowUtils.throwIf(schedule.getShowDate() == null || schedule.getStartTime() == null,
                ErrorCode.PARAMS_ERROR, "日期和时间不能为空");
        Long id = scheduleService.saveScheduleWithSeats(schedule);
        return ResultUtils.success(id);
    }

    /**
     * 批量创建排期。
     */
    @PostMapping("/batchSave")
    public BaseResponse<Integer> batchSave(@RequestBody List<Schedule> scheduleList) {
        ThrowUtils.throwIf(CollUtil.isEmpty(scheduleList), ErrorCode.PARAMS_ERROR);
        // 逐条保存并初始化座位
        for (Schedule s : scheduleList) {
            scheduleService.saveScheduleWithSeats(s);
        }
        return ResultUtils.success(scheduleList.size());
    }

    /**
     * 排期冲突校验。
     */
    @PostMapping("/checkConflict")
    public BaseResponse<Boolean> checkConflict(@RequestBody ConflictCheckRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        boolean hasConflict = scheduleService.checkConflict(request);
        return ResultUtils.success(hasConflict);
    }

    /**
     * 根据主键删除。
     */
    @DeleteMapping("remove/{id}")
    public BaseResponse<Boolean> remove(@PathVariable Long id) {
        return ResultUtils.success(scheduleService.removeById(id));
    }

    /**
     * 根据主键更新。
     */
    @PutMapping("update")
    public BaseResponse<Boolean> update(@RequestBody Schedule schedule) {
        boolean result = scheduleService.updateById(schedule);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 后台分页查询。
     */
    @PostMapping("page")
    public BaseResponse<Page<Schedule>> page(@RequestBody Page<Schedule> page) {
        return ResultUtils.success(scheduleService.page(page));
    }

    /**
     * 根据主键获取。
     */
    @GetMapping("getInfo/{id}")
    public BaseResponse<Schedule> getInfo(@PathVariable Long id) {
        return ResultUtils.success(scheduleService.getById(id));
    }

    /**
     * 查询所有。
     */
    @GetMapping("listAll")
    public BaseResponse<List<Schedule>> listAll() {
        return ResultUtils.success(scheduleService.list());
    }
}
