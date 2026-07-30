package com.limou.agent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.limou.agent.exception.BusinessException;
import com.limou.agent.exception.ErrorCode;
import com.limou.agent.model.dto.film.FilmQueryRequest;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.limou.agent.model.entity.Film;
import com.limou.agent.mapper.FilmMapper;
import com.limou.agent.service.FilmService;
import org.springframework.stereotype.Service;

/**
 * 影片 服务层实现。
 *
 * @author 李振南
 */
@Service
public class FilmServiceImpl extends ServiceImpl<FilmMapper, Film> implements FilmService {

    @Override
    public Page<Film> queryFilmPage(FilmQueryRequest filmQueryRequest) {
        if (filmQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        long pageNum = filmQueryRequest.getPageNum();
        long pageSize = filmQueryRequest.getPageSize();
        String keyword = filmQueryRequest.getKeyword();
        String type = filmQueryRequest.getType();
        String status = filmQueryRequest.getStatus();
        String sortField = filmQueryRequest.getSortField();
        String sortOrder = filmQueryRequest.getSortOrder();

        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("isDelete", 0);

        // 关键字模糊搜索（名称或简介）
        if (StrUtil.isNotBlank(keyword)) {
            queryWrapper.and("(name LIKE ? OR description LIKE ?)", "%" + keyword + "%", "%" + keyword + "%");
        }
        // 类型筛选
        if (StrUtil.isNotBlank(type)) {
            queryWrapper.like("type", type);
        }
        // 状态筛选
        if (StrUtil.isNotBlank(status)) {
            queryWrapper.eq("status", status);
        }
        // 排序
        queryWrapper.orderBy("createTime", false);
        if (StrUtil.isNotBlank(sortField)) {
            queryWrapper.orderBy(sortField, "ascend".equals(sortOrder));
        }

        return mapper.paginate(pageNum, pageSize, queryWrapper);
    }
}
