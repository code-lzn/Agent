package com.limou.agent.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.limou.agent.mapper.UserWatchedFilmMapper;
import com.limou.agent.model.entity.Film;
import com.limou.agent.model.entity.UserWatchedFilm;
import com.limou.agent.service.FilmService;
import com.limou.agent.service.UserWatchedFilmService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserWatchedFilmServiceImpl extends ServiceImpl<UserWatchedFilmMapper, UserWatchedFilm>
        implements UserWatchedFilmService {

    @Resource
    private FilmService filmService;

    @Override
    public void markAsWatched(Long userId, Long filmId) {
        QueryWrapper qw = QueryWrapper.create()
                .eq("userId", userId)
                .eq("filmId", filmId);
        if (this.count(qw) > 0) {
            return;
        }
        UserWatchedFilm entity = UserWatchedFilm.builder()
                .userId(userId)
                .filmId(filmId)
                .build();
        this.save(entity);
    }

    @Override
    public boolean isWatched(Long userId, Long filmId) {
        QueryWrapper qw = QueryWrapper.create()
                .eq("userId", userId)
                .eq("filmId", filmId);
        return this.count(qw) > 0;
    }

    @Override
    public List<Film> getMyWatchedFilms(Long userId) {
        QueryWrapper qw = QueryWrapper.create()
                .eq("userId", userId)
                .orderBy("createTime", false);
        List<UserWatchedFilm> records = this.list(qw);
        if (CollUtil.isEmpty(records)) {
            return Collections.emptyList();
        }
        List<Long> filmIds = records.stream()
                .map(UserWatchedFilm::getFilmId)
                .collect(Collectors.toList());
        return filmService.listByIds(filmIds);
    }

    @Override
    public long countByUserId(Long userId) {
        QueryWrapper qw = QueryWrapper.create().eq("userId", userId);
        return this.count(qw);
    }
}
