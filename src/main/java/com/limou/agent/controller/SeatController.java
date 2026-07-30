package com.limou.agent.controller;

import com.limou.agent.common.BaseResponse;
import com.limou.agent.common.ResultUtils;
import com.limou.agent.model.vo.SeatMapVO;
import com.mybatisflex.core.paginate.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.limou.agent.model.entity.Seat;
import com.limou.agent.service.SeatService;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * 座位 控制层。
 *
 * @author 李振南
 */
@RestController
@RequestMapping("/seat")
public class SeatController {

    @Autowired
    private SeatService seatService;

    // ========== 前台接口 ==========

    /**
     * 获取场次座位图。
     */
    @GetMapping("/seatmap/{scheduleId}")
    public BaseResponse<SeatMapVO> getSeatMap(@PathVariable Long scheduleId) {
        SeatMapVO seatMap = seatService.getSeatMap(scheduleId);
        return ResultUtils.success(seatMap);
    }

    // ========== 后台管理接口 ==========

    @PostMapping("save")
    public BaseResponse<Long> save(@RequestBody Seat seat) {
        boolean result = seatService.save(seat);
        if (!result) {
            return new BaseResponse<>(50001, null, "保存失败");
        }
        return ResultUtils.success(seat.getId());
    }

    @DeleteMapping("remove/{id}")
    public BaseResponse<Boolean> remove(@PathVariable Long id) {
        return ResultUtils.success(seatService.removeById(id));
    }

    @PutMapping("update")
    public BaseResponse<Boolean> update(@RequestBody Seat seat) {
        boolean result = seatService.updateById(seat);
        return ResultUtils.success(result);
    }

    @GetMapping("listAll")
    public BaseResponse<List<Seat>> listAll() {
        return ResultUtils.success(seatService.list());
    }

    @GetMapping("getInfo/{id}")
    public BaseResponse<Seat> getInfo(@PathVariable Long id) {
        return ResultUtils.success(seatService.getById(id));
    }

    @PostMapping("page")
    public BaseResponse<Page<Seat>> page(@RequestBody Page<Seat> page) {
        return ResultUtils.success(seatService.page(page));
    }

}
