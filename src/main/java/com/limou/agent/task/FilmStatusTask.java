package com.limou.agent.task;

import com.limou.agent.model.entity.Film;
import com.limou.agent.model.entity.Schedule;
import com.limou.agent.service.FilmService;
import com.limou.agent.service.ScheduleService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 影片状态定时任务：
 * - 准备上映(upcoming) 的影片，到达上映日期后自动转为正在上映(published)。
 * - 已放映完（有场次且所有场次都已过去）的影片自动下线(offline)。
 *
 * @author 李振南
 */
@Component
@Slf4j
public class FilmStatusTask implements ApplicationRunner {

    @Autowired
    private FilmService filmService;

    @Autowired
    private ScheduleService scheduleService;

    /**
     * 应用启动时立即执行一次状态补偿：修复后端错过凌晨 3 点定时任务窗口的情况
     * （例如后端凌晨 3 点未运行，次日开机启动后自动将已到上映日期的影片转为 published）。
     */
    @Override
    public void run(ApplicationArguments args) {
        log.info("影片状态任务：应用启动，执行状态补偿");
        try {
            transitionUpcomingToPublished();
        } catch (Exception e) {
            log.error("影片状态任务：启动补偿（自动上架）执行失败", e);
        }
        try {
            offlineFinishedFilms();
        } catch (Exception e) {
            log.error("影片状态任务：启动补偿（自动下线）执行失败", e);
        }
    }

    /**
     * 每天凌晨 3 点执行：status=upcoming 且 releaseDate <= 今天 → 自动转为 published
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void transitionUpcomingToPublished() {
        QueryWrapper qw = QueryWrapper.create()
                .eq("status", "upcoming")
                .le("releaseDate", Date.valueOf(LocalDate.now()));
        List<Film> films = filmService.list(qw);
        if (films.isEmpty()) {
            return;
        }
        films.forEach(f -> f.setStatus("published"));
        filmService.updateBatch(films);
        log.info("影片状态任务：{} 部准备上映影片已自动转为正在上映", films.size());
    }

    /**
     * 每天凌晨 3:30 执行：有场次且所有场次都已放映完的 published/hot 影片 → 自动下线。
     * 前台不再展示购票入口，历史订单保留。
     */
    @Scheduled(cron = "0 30 3 * * ?")
    public void offlineFinishedFilms() {
        List<Film> films = filmService.list(
                QueryWrapper.create().in("status", List.of("published", "hot")));
        if (films.isEmpty()) {
            return;
        }
        // 有场次的影片（排除从未排片的影片）
        Set<Long> allScheduleFilmIds = scheduleService.list()
                .stream().map(Schedule::getFilmId).collect(Collectors.toSet());
        // 有未来场次的影片（仍在映）
        Set<Long> futureFilmIds = scheduleService.list(
                        QueryWrapper.create().ge("showDate", Date.valueOf(LocalDate.now())))
                .stream().map(Schedule::getFilmId).collect(Collectors.toSet());

        List<Film> finished = films.stream()
                .filter(f -> allScheduleFilmIds.contains(f.getId()) && !futureFilmIds.contains(f.getId()))
                .collect(Collectors.toList());
        if (finished.isEmpty()) {
            return;
        }
        finished.forEach(f -> f.setStatus("offline"));
        filmService.updateBatch(finished);
        log.info("影片状态任务：{} 部已放映完的影片自动下线", finished.size());
    }
}
