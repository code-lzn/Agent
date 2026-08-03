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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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

    @Autowired
    @Lazy
    private OrderService orderService;

    @Override
    public List<ScheduleVO> queryScheduleList(Long filmId, Long cinemaId, Date showDate) {
        if (filmId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "影片ID不能为空");
        }

        Date queryDate = showDate != null ? showDate : Date.valueOf(LocalDate.now());

        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("filmId", filmId)
                .eq("status", "published")
                .ge("showDate", queryDate)
                .orderBy("showDate", true)
                .orderBy("startTime", true);

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

        // 3. 解析 seatTemplate 获取 VIP 行配置和行列覆盖
        Set<Integer> vipRows = new HashSet<>();
        Set<String> vipCells = new HashSet<>();
        Map<Integer, Integer> rowOverrides = new HashMap<>();
        if (cn.hutool.core.util.StrUtil.isNotBlank(hall.getSeatTemplate())) {
            try {
                cn.hutool.json.JSONObject tmpl = new cn.hutool.json.JSONObject(hall.getSeatTemplate());
                if (tmpl.containsKey("vipRows")) {
                    for (Object r : tmpl.getJSONArray("vipRows")) {
                        vipRows.add((Integer) r);
                    }
                }
                if (tmpl.containsKey("vipCells")) {
                    for (Object c : tmpl.getJSONArray("vipCells")) {
                        vipCells.add((String) c);
                    }
                }
                if (tmpl.containsKey("rowOverrides")) {
                    cn.hutool.json.JSONObject overrides = tmpl.getJSONObject("rowOverrides");
                    for (Map.Entry<String, Object> entry : overrides.entrySet()) {
                        try {
                            int r = Integer.parseInt(entry.getKey());
                            int c = ((Number) entry.getValue()).intValue();
                            rowOverrides.put(r, c);
                        } catch (Exception ignored) {
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 4. 批量初始化座位（支持逐行自定义列数）
        List<Seat> seats = new ArrayList<>();
        for (int row = 1; row <= hall.getRowCount(); row++) {
            int rowCols = rowOverrides.getOrDefault(row, hall.getColCount());
            for (int col = 1; col <= rowCols; col++) {
                Seat seat = new Seat();
                seat.setScheduleId(schedule.getId());
                seat.setHallId(hall.getId());
                seat.setRowNum(row);
                seat.setColNum(col);
                seat.setSeatLabel(row + "排" + col + "座");

                // 判断是否为 VIP 区
                boolean isVip = vipRows.contains(row) || vipCells.contains(row + "," + col);
                seat.setZone(isVip ? "vip" : "regular");

                seat.setStatus("available");
                seats.add(seat);
            }
        }

        seatService.saveBatch(seats);
        return schedule.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateSchedule(Schedule schedule) {
        if (schedule == null || schedule.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "场次ID不能为空");
        }
        Schedule old = this.getById(schedule.getId());
        if (old == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "场次不存在");
        }
        // 已放映场次禁止修改（历史数据锁定，避免影响已产生的订单/座位）
        if (isPastShowtime(old)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该场次已放映，禁止修改");
        }
        // 已有订单的场次：禁止修改影厅/影片/日期/时间/票价（PRD 4.2.3）
        long orderCount = orderService.count(QueryWrapper.create().eq("scheduleId", schedule.getId()));
        if (orderCount > 0 && hasCriticalChange(old, schedule)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该场次已有订单，禁止修改影厅/影片/时间/票价");
        }
        return this.updateById(schedule);
    }

    /**
     * 判断场次是否已放映：showDate < 今天，或 今天且 startTime 已过
     */
    private boolean isPastShowtime(Schedule s) {
        if (s.getShowDate() == null) return false;
        Date today = Date.valueOf(LocalDate.now());
        if (s.getShowDate().before(today)) return true;
        if (s.getShowDate().equals(today)) {
            String now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            if (s.getStartTime() != null && s.getStartTime().compareTo(now) < 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 关键字段是否有变更（影厅/影片/日期/时间/票价）
     */
    private boolean hasCriticalChange(Schedule old, Schedule submitted) {
        return !Objects.equals(old.getHallId(), submitted.getHallId())
                || !Objects.equals(old.getFilmId(), submitted.getFilmId())
                || !Objects.equals(old.getShowDate(), submitted.getShowDate())
                || !Objects.equals(old.getStartTime(), submitted.getStartTime())
                || !Objects.equals(old.getPrice(), submitted.getPrice());
    }
}
