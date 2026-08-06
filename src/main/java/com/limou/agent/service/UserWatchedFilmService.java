package com.limou.agent.service;

import com.limou.agent.model.entity.Film;
import com.limou.agent.model.entity.UserWatchedFilm;
import com.mybatisflex.core.service.IService;

import java.util.List;

public interface UserWatchedFilmService extends IService<UserWatchedFilm> {

    void markAsWatched(Long userId, Long filmId);

    boolean isWatched(Long userId, Long filmId);

    List<Film> getMyWatchedFilms(Long userId);

    long countByUserId(Long userId);
}
