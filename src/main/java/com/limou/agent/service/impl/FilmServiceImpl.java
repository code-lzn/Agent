package com.limou.agent.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.limou.agent.exception.BusinessException;
import com.limou.agent.exception.ErrorCode;
import com.limou.agent.model.dto.film.FilmQueryRequest;
import com.limou.agent.model.entity.Hall;
import com.limou.agent.model.entity.Schedule;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.limou.agent.model.entity.Film;
import com.limou.agent.mapper.FilmMapper;
import com.limou.agent.service.FilmService;
import com.limou.agent.service.HallService;
import com.limou.agent.service.ScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 影片 服务层实现。
 *
 * @author 李振南
 */
@Service
public class FilmServiceImpl extends ServiceImpl<FilmMapper, Film> implements FilmService {

    @Autowired
    @Lazy
    private ScheduleService scheduleService;

    @Autowired
    @Lazy
    private HallService hallService;

    @Override
    public void enrichFormatTags(List<Film> films) {
        if (CollUtil.isEmpty(films)) return;

        List<Long> filmIds = films.stream().map(Film::getId).collect(Collectors.toList());

        // 查这些影片的所有已发布排期
        List<Schedule> schedules = scheduleService.list(
                QueryWrapper.create()
                        .in("filmId", filmIds.toArray())
                        .eq("status", "published"));
        if (CollUtil.isEmpty(schedules)) {
            films.forEach(f -> f.setFormatTags(new ArrayList<>()));
            return;
        }

        // 查关联影厅的厅型
        Set<Long> hallIds = schedules.stream()
                .map(Schedule::getHallId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> hallTypeMap = new HashMap<>();
        if (!hallIds.isEmpty()) {
            List<Hall> halls = hallService.listByIds(new ArrayList<>(hallIds));
            hallTypeMap = halls.stream()
                    .filter(h -> h.getHallType() != null && !h.getHallType().isEmpty())
                    .collect(Collectors.toMap(Hall::getId, Hall::getHallType, (a, b) -> a));
        }

        // filmId → 厅型集合
        Map<Long, Set<String>> filmTags = new HashMap<>();
        for (Schedule s : schedules) {
            String hallType = hallTypeMap.get(s.getHallId());
            if (hallType != null && !hallType.isEmpty()) {
                filmTags.computeIfAbsent(s.getFilmId(), k -> new LinkedHashSet<>()).add(hallType);
            }
        }

        // 设置到 Film
        for (Film film : films) {
            Set<String> tags = filmTags.get(film.getId());
            film.setFormatTags(tags != null ? new ArrayList<>(tags) : new ArrayList<>());
        }
    }

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
        // 状态筛选（优先多状态 IN 查询，其次单状态）
        if (CollUtil.isNotEmpty(filmQueryRequest.getStatusList())) {
            queryWrapper.in("status", filmQueryRequest.getStatusList());
        } else if (StrUtil.isNotBlank(status)) {
            queryWrapper.eq("status", status);
        }
        // 最低评分筛选
        if (filmQueryRequest.getMinRating() != null) {
            queryWrapper.ge("rating", filmQueryRequest.getMinRating());
        }
        // 排除指定影片
        if (filmQueryRequest.getExcludeFilmId() != null) {
            queryWrapper.ne("id", filmQueryRequest.getExcludeFilmId());
        }
        // 排序
        queryWrapper.orderBy("createTime", false);
        if (StrUtil.isNotBlank(sortField)) {
            queryWrapper.orderBy(sortField, "ascend".equals(sortOrder));
        }

        return mapper.paginate(pageNum, pageSize, queryWrapper);
    }
}
