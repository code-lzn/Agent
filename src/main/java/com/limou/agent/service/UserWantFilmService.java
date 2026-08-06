package com.limou.agent.service;

import com.limou.agent.model.entity.Film;
import com.limou.agent.model.entity.UserWantFilm;
import com.mybatisflex.core.service.IService;

import java.util.List;

public interface UserWantFilmService extends IService<UserWantFilm> {

    /**
     * 切换想看状态，返回 true=已标记想看，false=已取消想看
     */
    boolean toggleWantToSee(Long userId, Long filmId);

    boolean isWanted(Long userId, Long filmId);

    List<Film> getMyWantToSeeFilms(Long userId);

    void removeWantToSee(Long userId, Long filmId);

    long countByUserId(Long userId);
}
