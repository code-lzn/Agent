package com.limou.agent.controller;

import com.limou.agent.common.BaseResponse;
import com.limou.agent.common.ResultUtils;
import com.limou.agent.model.entity.Film;
import com.limou.agent.model.entity.User;
import com.limou.agent.service.UserService;
import com.limou.agent.service.UserWantFilmService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/userWantFilm")
public class UserWantFilmController {

    @Resource
    private UserWantFilmService userWantFilmService;

    @Resource
    private UserService userService;

    private Long getLoginUserId(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return loginUser.getId();
    }

    @PostMapping("/toggle/{filmId}")
    public BaseResponse<Map<String, Object>> toggleWantToSee(@PathVariable Long filmId,
                                                              HttpServletRequest request) {
        Long userId = getLoginUserId(request);
        boolean wanted = userWantFilmService.toggleWantToSee(userId, filmId);
        Map<String, Object> result = new HashMap<>();
        result.put("wanted", wanted);
        result.put("filmId", filmId);
        return ResultUtils.success(result);
    }

    @GetMapping("/isWanted/{filmId}")
    public BaseResponse<Map<String, Object>> isWanted(@PathVariable Long filmId,
                                                       HttpServletRequest request) {
        Long userId = getLoginUserId(request);
        boolean wanted = userWantFilmService.isWanted(userId, filmId);
        Map<String, Object> result = new HashMap<>();
        result.put("wanted", wanted);
        return ResultUtils.success(result);
    }

    @GetMapping("/my")
    public BaseResponse<List<Film>> getMyWantToSee(HttpServletRequest request) {
        Long userId = getLoginUserId(request);
        List<Film> films = userWantFilmService.getMyWantToSeeFilms(userId);
        return ResultUtils.success(films);
    }

    @DeleteMapping("/remove/{filmId}")
    public BaseResponse<Void> removeWantToSee(@PathVariable Long filmId,
                                               HttpServletRequest request) {
        Long userId = getLoginUserId(request);
        userWantFilmService.removeWantToSee(userId, filmId);
        return ResultUtils.success(null);
    }

    @GetMapping("/count")
    public BaseResponse<Map<String, Object>> count(HttpServletRequest request) {
        Long userId = getLoginUserId(request);
        long count = userWantFilmService.countByUserId(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("count", count);
        return ResultUtils.success(result);
    }
}
