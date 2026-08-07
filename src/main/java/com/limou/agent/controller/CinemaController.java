package com.limou.agent.controller;

import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.StrUtil;
import com.limou.agent.common.BaseResponse;
import com.limou.agent.common.ResultUtils;
import com.limou.agent.exception.BusinessException;
import com.limou.agent.exception.ErrorCode;
import com.limou.agent.exception.ThrowUtils;
import com.limou.agent.model.dto.cinema.CinemaFilterRequest;
import com.limou.agent.service.ScheduleService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.web.bind.annotation.DeleteMapping;
import java.sql.Date;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.beans.factory.annotation.Autowired;
import com.limou.agent.model.entity.Cinema;
import com.limou.agent.service.CinemaService;
import org.springframework.web.bind.annotation.RestController;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;

/**
 *  控制层。
 *
 * @author 李振南
 */
@RestController
@RequestMapping("/cinema")
public class CinemaController {

    @Autowired
    private CinemaService cinemaService;

    @Autowired
    private ScheduleService scheduleService;

    /**
     * 保存。
     *
     * @param cinema
     * @return {@code true} 保存成功，{@code false} 保存失败
     */
    @PostMapping("save")
    public BaseResponse<Boolean> save(@RequestBody Cinema cinema) {
        ThrowUtils.throwIf(cinema == null, ErrorCode.PARAMS_ERROR, "参数为空");
        checkCinemaPhone(cinema.getPhone());
        return ResultUtils.success(cinemaService.save(cinema));
    }

    /**
     * 根据主键删除。
     *
     * @param id 主键
     * @return {@code true} 删除成功，{@code false} 删除失败
     */
    @DeleteMapping("remove/{id}")
    public BaseResponse<Boolean> remove(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        // PRD 3.3.3.2 交互规则③：有"未放映"场次（今天及以后）的影院禁止删除，需先清空场次
        long scheduleCount = scheduleService.count(QueryWrapper.create()
                .eq("cinemaId", id)
                .ge("showDate", Date.valueOf(LocalDate.now())));
        ThrowUtils.throwIf(scheduleCount > 0, ErrorCode.OPERATION_ERROR,
                "该影院存在未放映的场次，禁止删除，请先清空场次");
        boolean result = cinemaService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据主键更新。
     *
     * @param cinema
     * @return {@code true} 更新成功，{@code false} 更新失败
     */
    @PutMapping("update")
    public BaseResponse<Boolean> update(@RequestBody Cinema cinema) {
        ThrowUtils.throwIf(cinema == null, ErrorCode.PARAMS_ERROR, "参数为空");
        checkCinemaPhone(cinema.getPhone());
        return ResultUtils.success(cinemaService.updateById(cinema));
    }

    /**
     * 联系电话格式校验：手机号（如 13800138000）或带区号座机（如 020-88888888）。
     * 空值放行（兼容历史数据），非空则必须格式正确。
     */
    private void checkCinemaPhone(String phone) {
        if (StrUtil.isBlank(phone)) {
            return;
        }
        if (!Validator.isMobile(phone) && !phone.matches("^0\\d{2,3}-?\\d{7,8}$")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "联系电话格式不正确：手机号如 13800138000，座机需带区号如 020-88888888");
        }
    }

    /**
     * 查询所有。
     *
     * @return 所有数据
     */
    @GetMapping("list")
    public BaseResponse<List<Cinema>> list() {
        return ResultUtils.success(cinemaService.list());
    }

    /**
     * 多条件筛选影院（品牌、区域、服务、排序）。
     *
     * @param request 筛选条件
     * @return 符合条件的影院列表
     */
    @GetMapping("filter")
    public BaseResponse<List<Cinema>> filter(CinemaFilterRequest request) {
        return ResultUtils.success(cinemaService.filterCinemas(request));
    }

    /**
     * 根据主键获取（可选传入用户坐标以通过高德 API 计算距离）。
     *
     * @param id 主键
     * @param userLat 用户纬度（可选）
     * @param userLng 用户经度（可选）
     * @return 详情
     */
    @GetMapping("getInfo/{id}")
    public BaseResponse<Cinema> getInfo(@PathVariable Long id,
                                         @RequestParam(required = false) BigDecimal userLat,
                                         @RequestParam(required = false) BigDecimal userLng) {
        Cinema cinema = cinemaService.getById(id);
        if (cinema != null && userLat != null && userLng != null) {
            cinemaService.computeAmapDistance(cinema, userLat, userLng);
        }
        return ResultUtils.success(cinema);
    }

    /**
     * 分页查询。
     *
     * @param page 分页对象
     * @return 分页对象
     */
    @GetMapping("page")
    public BaseResponse<Page<Cinema>> page(Page<Cinema> page) {
        return ResultUtils.success(cinemaService.page(page));
    }

}
