package com.limou.agent.ai.movie.tools;

import cn.hutool.json.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limou.agent.ai.tools.BaseTool;
import com.limou.agent.mapper.HallMapper;
import com.limou.agent.mapper.ScheduleMapper;
import com.limou.agent.mapper.SeatMapper;
import com.limou.agent.model.entity.Hall;
import com.limou.agent.model.entity.Schedule;
import com.limou.agent.model.entity.Seat;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 场次搜索工具
 * 按影片、影院、日期、厅型搜索放映场次
 */
@Slf4j
@Component
public class SearchSchedulesTool extends BaseTool {

    @Resource
    private ScheduleMapper scheduleMapper;

    @Resource
    private HallMapper hallMapper;

    @Resource
    private SeatMapper seatMapper;

    @Resource
    private ObjectMapper objectMapper;

    @Tool(description = "搜索放映场次，需传入影片ID。可选影院ID、日期、厅型。返回场次列表JSON，含影厅名、时间、价格、余座数")
    public String searchSchedules(
            @ToolParam(description = "影片ID（必填）") Long filmId,
            @ToolParam(description = "影院ID（可选）", required = false) Long cinemaId,
            @ToolParam(description = "放映日期 yyyy-MM-dd（可选）") String showDate,
            @ToolParam(description = "厅型偏好，如 IMAX/杜比/普通/4DX/VIP（可选）") String hallType
    ) {
        try {
            // 1. 查询场次
            QueryWrapper wrapper = QueryWrapper.create()
                    .eq(Schedule::getFilmId, filmId)
                    .eq(Schedule::getStatus, "published");

            if (cinemaId != null) {
                wrapper.eq(Schedule::getCinemaId, cinemaId);
            }
            if (showDate != null && !showDate.isBlank()) {
                wrapper.eq(Schedule::getShowDate, Date.valueOf(showDate));
            }
            // hallType 过滤需要在查询 hall 表后进行

            wrapper.orderBy(Schedule::getShowDate, true)
                    .orderBy(Schedule::getStartTime, true);

            List<Schedule> schedules = scheduleMapper.selectListByQuery(wrapper);

            // 2. 查询关联的影厅信息
            Set<Long> hallIds = schedules.stream()
                    .map(Schedule::getHallId)
                    .collect(Collectors.toSet());

            Map<Long, Hall> hallMap = new HashMap<>();
            if (!hallIds.isEmpty()) {
                QueryWrapper hallWrapper = QueryWrapper.create()
                        .in(Hall::getId, hallIds);
                List<Hall> halls = hallMapper.selectListByQuery(hallWrapper);
                for (Hall hall : halls) {
                    hallMap.put(hall.getId(), hall);
                }
            }

            // 3. 构建返回结果（含余座统计）
            List<Map<String, Object>> sessionList = new ArrayList<>();
            for (Schedule s : schedules) {
                Hall hall = hallMap.get(s.getHallId());
                if (hall == null) continue;

                // 厅型过滤
                if (hallType != null && !hallType.isBlank()
                        && !hall.getHallType().contains(hallType)) {
                    continue;
                }

                // 统计可用座位数
                long availableSeats = seatMapper.selectCountByQuery(
                        QueryWrapper.create()
                                .eq(Seat::getScheduleId, s.getId())
                                .eq(Seat::getStatus, "available")
                );

                // 统计总座位数
                long totalSeats = seatMapper.selectCountByQuery(
                        QueryWrapper.create()
                                .eq(Seat::getScheduleId, s.getId())
                );

                Map<String, Object> map = new HashMap<>();
                map.put("scheduleId", s.getId());
                map.put("filmId", s.getFilmId());
                map.put("cinemaId", s.getCinemaId());
                map.put("hallId", s.getHallId());
                map.put("hallName", hall.getName());
                map.put("hallType", hall.getHallType());
                map.put("showDate", s.getShowDate() != null ? s.getShowDate().toString() : null);
                map.put("startTime", s.getStartTime() != null ? s.getStartTime().toString() : null);
                map.put("endTime", s.getEndTime() != null ? s.getEndTime().toString() : null);
                map.put("price", s.getPrice());
                map.put("vipPrice", s.getVipPrice());
                map.put("availableSeats", availableSeats);
                map.put("totalSeats", totalSeats);
                sessionList.add(map);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("sessions", sessionList);
            result.put("total", sessionList.size());

            String json = objectMapper.writeValueAsString(result);
            log.info("searchSchedules 查询结果: filmId={}, cinemaId={}, showDate={}, hallType={}, 找到{}个场次",
                    filmId, cinemaId, showDate, hallType, sessionList.size());
            return json;

        } catch (Exception e) {
            log.error("searchSchedules 查询失败", e);
            return "{\"sessions\":[],\"total\":0,\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    @Override
    public String getToolName() {
        return "searchSchedules";
    }

    @Override
    public String getDisplayName() {
        return "搜索场次";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        Long filmId = arguments.getLong("filmId");
        return String.format("[工具调用] 搜索场次 filmId=%d", filmId);
    }
}
