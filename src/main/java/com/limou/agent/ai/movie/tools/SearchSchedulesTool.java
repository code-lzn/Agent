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
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
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

    @Tool(description = "搜索放映场次，需传入影片ID。可选影院ID、日期、开始时间（HH:mm）、厅型、厅名。返回场次列表JSON，含影厅名、时间、价格、余座数")
    public String searchSchedules(
            @ToolParam(description = "影片ID（必填）") Long filmId,
            @ToolParam(description = "影院ID（可选）", required = false) Long cinemaId,
            @ToolParam(description = "放映日期 yyyy-MM-dd（可选）") String showDate,
            @ToolParam(description = "厅型偏好，只能传类型简称如 IMAX/杜比/普通/4DX/VIP/巨幕/激光。不要传影厅全名如"+"杜比全景声厅"+"（传"+"杜比"+"即可）", required = false) String hallType,
            @ToolParam(description = "期望的开始时间 HH:mm，如 14:00。传入后只返回该时间前后3小时内的场次，并按时间接近程度排序（可选）", required = false) String startTime,
            @ToolParam(description = "影厅名称关键词，如 杜比全景声厅/LED厅。与厅型独立，可同时传入（非必填）", required = false) String hallName
    ) {
        try {
            // 解析目标时间
            LocalTime targetTime = null;
            if (startTime != null && !startTime.isBlank()) {
                try {
                    targetTime = LocalTime.parse(startTime.trim());
                } catch (Exception e) {
                    log.warn("开始时间格式错误: {}", startTime);
                }
            }
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

                // ★ 厅型过滤 —— 双向模糊匹配，兼容简称和全名
                // LLM 可能传类型简称 "杜比"，也可能传厅名全称 "杜比全景声厅"
                if (hallType != null && !hallType.isBlank()) {
                    String input = hallType.trim();
                    String dbType = hall.getHallType();   // "杜比" / "IMAX"
                    String dbName = hall.getName();        // "杜比全景声厅" / "IMAX激光厅"
                    boolean matched = (dbType != null && (dbType.contains(input) || input.contains(dbType)))
                            || (dbName != null && (dbName.contains(input) || input.contains(dbName)));
                    if (!matched) {
                        continue;
                    }
                }

                // ★ 厅名过滤 —— 双向模糊匹配，用于精确筛选特定影厅
                if (hallName != null && !hallName.isBlank()) {
                    String input = hallName.trim();
                    String dbName = hall.getName();
                    if (dbName == null || (!dbName.contains(input) && !input.contains(dbName))) {
                        continue;
                    }
                }

                // ★ 时间范围过滤 —— 只保留目标时间前后3小时内的场次
                long timeDiffMinutes = 0;
                if (targetTime != null && s.getStartTime() != null) {
                    try {
                        LocalTime scheduleTime = LocalTime.parse(s.getStartTime());
                        timeDiffMinutes = Math.abs(ChronoUnit.MINUTES.between(targetTime, scheduleTime));
                        if (timeDiffMinutes > 180) { // 超过3小时 → 跳过
                            continue;
                        }
                    } catch (Exception ignored) {}
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
                // 时间距离（分钟），供排序使用
                if (targetTime != null) {
                    map.put("timeDiffMinutes", timeDiffMinutes);
                }
                sessionList.add(map);
            }

            // ★ 按时间接近程度排序（有目标时间时）
            if (targetTime != null) {
                sessionList.sort(Comparator.comparingLong(
                        m -> ((Number) m.getOrDefault("timeDiffMinutes", 0L)).longValue()));
            }

            Map<String, Object> result = new HashMap<>();
            result.put("sessions", sessionList);
            result.put("total", sessionList.size());

            String json = objectMapper.writeValueAsString(result);
            log.info("searchSchedules 查询结果: filmId={}, cinemaId={}, showDate={}, hallType={}, startTime={}, 找到{}个场次",
                    filmId, cinemaId, showDate, hallType, startTime, sessionList.size());
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
