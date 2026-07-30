package com.limou.agent.controller;

import com.limou.agent.common.BaseResponse;
import com.limou.agent.common.ResultUtils;
import com.limou.agent.exception.BusinessException;
import com.limou.agent.exception.ErrorCode;
import com.limou.agent.exception.ThrowUtils;
import com.limou.agent.model.dto.film.FilmQueryRequest;
import com.mybatisflex.core.paginate.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.limou.agent.model.entity.Film;
import com.limou.agent.service.FilmService;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * 影片 控制层。
 *
 * @author 李振南
 */
@RestController
@RequestMapping("/film")
public class FilmController {

    @Autowired
    private FilmService filmService;

    // ========== 前台接口 ==========

    /** 影片列表（筛选 + 排序 + 分页）。
     * 前端传 status=hot 取热映，status=upcoming 取即将上映
     */
    @GetMapping("/list")
    public BaseResponse<Page<Film>> listFilm(FilmQueryRequest filmQueryRequest) {
        Page<Film> filmPage = filmService.queryFilmPage(filmQueryRequest);
        return ResultUtils.success(filmPage);
    }

    /**
     * 影片详情。
     *
     * @param id 影片ID
     * @return 影片信息
     */
    @GetMapping("/{id}")
    public BaseResponse<Film> getFilm(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        Film film = filmService.getById(id);
        ThrowUtils.throwIf(film == null, ErrorCode.NOT_FOUND_ERROR, "影片不存在");
        return ResultUtils.success(film);
    }

    // ========== 后台管理接口 ==========

    /**
     * 保存。
     */
    @PostMapping("save")
    public BaseResponse<Long> save(@RequestBody Film film) {
        boolean result = filmService.save(film);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(film.getId());
    }

    /**
     * 根据主键删除。
     */
    @DeleteMapping("remove/{id}")
    public BaseResponse<Boolean> remove(@PathVariable Long id) {
        boolean result = filmService.removeById(id);
        return ResultUtils.success(result);
    }

    /**
     * 根据主键更新。
     */
    @PutMapping("update")
    public BaseResponse<Boolean> update(@RequestBody Film film) {
        boolean result = filmService.updateById(film);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 查询所有。
     */
    @GetMapping("listAll")
    public BaseResponse<List<Film>> listAll() {
        List<Film> list = filmService.list();
        return ResultUtils.success(list);
    }

    /**
     * 后台分页查询。
     */
    @PostMapping("page")
    public BaseResponse<Page<Film>> page(@RequestBody FilmQueryRequest filmQueryRequest) {
        Page<Film> filmPage = filmService.queryFilmPage(filmQueryRequest);
        return ResultUtils.success(filmPage);
    }

    /**
     * 根据主键获取。
     */
    @GetMapping("getInfo/{id}")
    public BaseResponse<Film> getInfo(@PathVariable Long id) {
        Film film = filmService.getById(id);
        return ResultUtils.success(film);
    }

    /**
     * 修改影片状态（后台管理）。
     */
    @PutMapping("/status/{id}")
    public BaseResponse<Boolean> updateStatus(@PathVariable Long id, @RequestParam String status) {
        Film film = filmService.getById(id);
        ThrowUtils.throwIf(film == null, ErrorCode.NOT_FOUND_ERROR);
        film.setStatus(status);
        boolean result = filmService.updateById(film);
        return ResultUtils.success(result);
    }

}
