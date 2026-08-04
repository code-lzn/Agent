package com.limou.agent.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.limou.agent.exception.BusinessException;
import com.limou.agent.exception.ErrorCode;
import com.limou.agent.model.dto.schedule.ConflictCheckRequest;
import com.limou.agent.model.entity.Cinema;
import com.limou.agent.model.entity.Film;
import com.limou.agent.model.entity.Hall;
import com.limou.agent.model.entity.Seat;
import com.limou.agent.model.entity.Schedule;
import com.limou.agent.model.vo.ScheduleVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.limou.agent.mapper.ScheduleMapper;
import com.limou.agent.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 排期 服务层实现。
 *
 * @author 李振南
 */
@Service
public class ScheduleServiceImpl extends ServiceImpl<ScheduleMapper, Schedule> implements ScheduleService {

    @Autowired
    private FilmService filmService;

    @Autowired
    private CinemaService cinemaService;

    @Autowired
    private HallService hallService;

    @Autowired
    @Lazy
    private SeatService seatService;

    @Override
    public List<ScheduleVO> queryScheduleList(Long filmId, Long cinemaId, Date showDate) {
        if (filmId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "影片ID不能为空");
        }

        Date queryDate = showDate != null ? showDate : Date.valueOf(LocalDate.now());

        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("filmId", filmId)
                .eq("status", "published")
                .orderBy("showDate", true)
                .orderBy("startTime", true);

        // 指定日期 → 精确匹配当天（C端日期 Tab）；未指定 → 从今天起全部未来场次
        if (showDate != null) {
            queryWrapper.eq("showDate", queryDate);
        } else {
            queryWrapper.ge("showDate", queryDate);
        }

        if (cinemaId != null) {
            queryWrapper.eq("cinemaId", cinemaId);
        }

        List<Schedule> scheduleList = mapper.selectListByQuery(queryWrapper);
        if (CollUtil.isEmpty(scheduleList)) {
            return new ArrayList<>();
        }

        Set<Long> filmIds = new HashSet<>();
        Set<Long> cinemaIds = new HashSet<>();
        Set<Long> hallIds = new HashSet<>();
        for (Schedule s : scheduleList) {
            filmIds.add(s.getFilmId());
            cinemaIds.add(s.getCinemaId());
            hallIds.add(s.getHallId());
        }

        Map<Long, Film> filmMap = filmService.listByIds(filmIds).stream()
                .collect(Collectors.toMap(Film::getId, f -> f, (a, b) -> a));
        Map<Long, Cinema> cinemaMap = cinemaService.listByIds(cinemaIds).stream()
                .collect(Collectors.toMap(Cinema::getId, c -> c, (a, b) -> a));
        Map<Long, Hall> hallMap = hallService.listByIds(hallIds).stream()
                .collect(Collectors.toMap(Hall::getId, h -> h, (a, b) -> a));

        List<ScheduleVO> voList = new ArrayList<>();
        for (Schedule schedule : scheduleList) {
            ScheduleVO vo = new ScheduleVO();
            BeanUtil.copyProperties(schedule, vo);

            Film film = filmMap.get(schedule.getFilmId());
            if (film != null) {
                vo.setFilmName(film.getName());
                vo.setFilmPoster(film.getPosterUrl());
                vo.setFilmDuration(film.getDuration());
                vo.setFilmRating(film.getRating() != null ? film.getRating().toString() : null);
                vo.setFilmType(film.getType());
            }

            Cinema cinema = cinemaMap.get(schedule.getCinemaId());
            if (cinema != null) {
                vo.setCinemaName(cinema.getName());
                vo.setCinemaAddress(cinema.getAddress());
            }

            Hall hall = hallMap.get(schedule.getHallId());
            if (hall != null) {
                vo.setHallName(hall.getName());
                vo.setHallType(hall.getHallType());
                vo.setHallRowCount(hall.getRowCount());
                vo.setHallColCount(hall.getColCount());
            }

            voList.add(vo);
        }

        return voList;
    }

    @Override
    public boolean checkConflict(ConflictCheckRequest request) {
        if (request.getHallId() == null || request.getShowDate() == null
                || request.getStartTime() == null || request.getEndTime() == null) {
            return false;
        }

        // 同影厅、同日期、时段重叠检测
        // 冲突条件: 已有排期的 startTime < 新排期的 endTime 且 已有排期的 endTime > 新排期的 startTime
        QueryWrapper qw = QueryWrapper.create()
                .eq("hallId", request.getHallId())
                .eq("showDate", request.getShowDate())
                .ne("status", "offline")
                .and("startTime < ?", request.getEndTime())
                .and("endTime > ?", request.getStartTime());

        // 编辑排期时排除自身
        if (request.getExcludeScheduleId() != null) {
            qw.ne("id", request.getExcludeScheduleId());
        }

        return mapper.selectCountByQuery(qw) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveScheduleWithSeats(Schedule schedule) {
        // 1. 保存排期
        boolean saved = super.save(schedule);
        if (!saved) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "排期保存失败");
        }

        // 2. 查影厅信息
        Hall hall = hallService.getById(schedule.getHallId());
        if (hall == null || hall.getRowCount() == null || hall.getColCount() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "影厅信息不完整，无法初始化座位");
        }

        // 3. 解析 seatTemplate 并初始化座位
        HallLayout layout = parseHallLayout(hall);
        seatService.saveBatch(buildSeats(schedule, hall.getId(), layout));
        return schedule.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchSaveWithSeats(List<Schedule> scheduleList) {
        if (CollUtil.isEmpty(scheduleList)) {
            return 0;
        }

        // 1. 先保存所有场次，拿到自增 ID（单事务，避免逐条提交）
        for (Schedule s : scheduleList) {
            boolean saved = super.save(s);
            if (!saved) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "排期保存失败");
            }
        }

        // 2. 按影厅一次性解析 seatTemplate（避免每条场次重复查厅/解析）
        Map<Long, HallLayout> layoutMap = new HashMap<>();
        for (Schedule s : scheduleList) {
            if (layoutMap.containsKey(s.getHallId())) {
                continue;
            }
            Hall hall = hallService.getById(s.getHallId());
            if (hall == null || hall.getRowCount() == null || hall.getColCount() == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "影厅信息不完整，无法初始化座位");
            }
            layoutMap.put(hall.getId(), parseHallLayout(hall));
        }

        // 3. 为所有场次生成座位
        List<Seat> allSeats = new ArrayList<>();
        for (Schedule s : scheduleList) {
            HallLayout layout = layoutMap.get(s.getHallId());
            if (layout == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "影厅信息不完整，无法初始化座位");
            }
            allSeats.addAll(buildSeats(s, s.getHallId(), layout));
        }

        // 4. 一次性批量插入所有座位
        seatService.saveBatch(allSeats);
        return scheduleList.size();
    }

    /**
     * 影厅座位布局（从 seatTemplate 解析）。
     */
    private static class HallLayout {
        int rowCount;
        int colCount;
        Set<Integer> vipRows = new HashSet<>();
        Set<String> vipCells = new HashSet<>();
        Map<Integer, Integer> rowOverrides = new HashMap<>();
    }

    private HallLayout parseHallLayout(Hall hall) {
        HallLayout layout = new HallLayout();
        layout.rowCount = hall.getRowCount();
        layout.colCount = hall.getColCount();
        if (cn.hutool.core.util.StrUtil.isNotBlank(hall.getSeatTemplate())) {
            try {
                cn.hutool.json.JSONObject tmpl = new cn.hutool.json.JSONObject(hall.getSeatTemplate());
                if (tmpl.containsKey("vipRows")) {
                    for (Object r : tmpl.getJSONArray("vipRows")) {
                        layout.vipRows.add((Integer) r);
                    }
                }
                if (tmpl.containsKey("vipCells")) {
                    for (Object c : tmpl.getJSONArray("vipCells")) {
                        layout.vipCells.add((String) c);
                    }
                }
                if (tmpl.containsKey("rowOverrides")) {
                    cn.hutool.json.JSONObject overrides = tmpl.getJSONObject("rowOverrides");
                    for (Map.Entry<String, Object> entry : overrides.entrySet()) {
                        try {
                            int r = Integer.parseInt(entry.getKey());
                            int c = ((Number) entry.getValue()).intValue();
                            layout.rowOverrides.put(r, c);
                        } catch (Exception ignored) {
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return layout;
    }

    private List<Seat> buildSeats(Schedule schedule, Long hallId, HallLayout layout) {
        List<Seat> seats = new ArrayList<>();
        for (int row = 1; row <= layout.rowCount; row++) {
            int rowCols = layout.rowOverrides.getOrDefault(row, layout.colCount);
            for (int col = 1; col <= rowCols; col++) {
                Seat seat = new Seat();
                seat.setScheduleId(schedule.getId());
                seat.setHallId(hallId);
                seat.setRowNum(row);
                seat.setColNum(col);
                seat.setSeatLabel(row + "排" + col + "座");

                // 判断是否为 VIP 区
                boolean isVip = layout.vipRows.contains(row) || layout.vipCells.contains(row + "," + col);
                seat.setZone(isVip ? "vip" : "regular");

                seat.setStatus("available");
                seats.add(seat);
            }
        }
        return seats;
    }
}
