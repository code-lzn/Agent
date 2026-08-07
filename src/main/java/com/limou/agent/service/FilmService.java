package com.limou.agent.service;

import com.limou.agent.model.dto.film.FilmQueryRequest;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.service.IService;
import com.limou.agent.model.entity.Film;

import java.util.List;

/**
 * 影片 服务层。
 *
 * @author 李振南
 */
public interface FilmService extends IService<Film> {

    /**
     * 分页查询影片列表（已发布）
     *
     * @param filmQueryRequest 查询请求
     * @return 分页结果
     */
    Page<Film> queryFilmPage(FilmQueryRequest filmQueryRequest);

    /**
     * 填充制式标签（从排片+影厅汇总 IMAX/杜比/2D 等真实数据）
     */
    void enrichFormatTags(List<Film> films);
}
