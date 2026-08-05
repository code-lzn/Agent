package com.limou.agent.ai.movie.graph.nodes;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.limou.agent.ai.graph.GraphNode;
import com.limou.agent.ai.movie.MovieStateManager;
import com.limou.agent.ai.movie.graph.MovieGraphState;
import com.limou.agent.ai.movie.graph.MovieIntent;
import com.limou.agent.ai.movie.tools.SearchSchedulesTool;
import com.limou.agent.model.dto.movie.ConversationState;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalTime;

/**
 * 搜索场次节点
 */
@Slf4j
public class SearchScheduleNode implements GraphNode<MovieGraphState> {

    private final SearchSchedulesTool tool;
    private final MovieStateManager stateManager;

    public SearchScheduleNode(SearchSchedulesTool tool, MovieStateManager stateManager) {
        this.tool = tool;
        this.stateManager = stateManager;
    }

    @Override
    public MovieGraphState execute(MovieGraphState state) {
        if (state.isBlocked()) {
            return state;
        }

        ConversationState convState = state.getConvState();

        // ★ 安全网：filmId 未解析时不能查场次（否则会查出全部场次）
        if (convState.getFilmId() == null && convState.getFilmName() != null) {
            String error = "{\"error\":\"请先确认影片\",\"filmName\":\""
                    + convState.getFilmName() + "\"}";
            state.setToolResult(error);
            state.setToolName(MovieIntent.SEARCH_SCHEDULE.getCode());
            log.warn("SearchSchedule 被阻止: filmId=null, filmName={}, conversationId={}",
                    convState.getFilmName(), state.getConversationId());
            return state;
        }

        String result = tool.searchSchedules(
                convState.getFilmId(),
                convState.getCinemaId(),
                convState.getShowDate(),
                convState.getHallType(),
                convState.getStartTime(),
                null);  // hallName 由 LLM 从用户输入提取后直接传入工具，不从 state 取

        state.setToolResult(result);
        state.setToolName(MovieIntent.SEARCH_SCHEDULE.getCode());
        persistResolvedSchedule(result, convState, state.getConversationId());
        log.info("SearchSchedule 完成: conversationId={}", state.getConversationId());
        return state;
    }

    private void persistResolvedSchedule(String result, ConversationState convState, String conversationId) {
        try {
            JSONArray sessions = JSONUtil.parseObj(result).getJSONArray("sessions");
            if (sessions == null || sessions.isEmpty()) return;

            JSONObject selected = null;
            String requestedTime = convState.getStartTime();
            if (requestedTime != null && !requestedTime.isBlank()) {
                for (int i = 0; i < sessions.size(); i++) {
                    JSONObject session = sessions.getJSONObject(i);
                    if (!sameTime(requestedTime, session.getStr("startTime"))) continue;
                    if (selected != null) return;
                    selected = session;
                }
            }
            // ★ 文字选场次：按厅型/厅名/影院名匹配唯一场次写回（用户"选IMAX厅""选第二个"等）
            if (selected == null && has(convState.getHallType())) {
                selected = matchUnique(sessions, "hallType", convState.getHallType());
            }
            if (selected == null && has(convState.getHallName())) {
                selected = matchUnique(sessions, "hallName", convState.getHallName());
            }
            if (selected == null && has(convState.getCinemaName())) {
                selected = matchUnique(sessions, "cinemaName", convState.getCinemaName());
            }
            if (selected == null && sessions.size() == 1) {
                selected = sessions.getJSONObject(0);
            }

            if (selected == null || selected.getLong("scheduleId") == null) return;
            convState.setScheduleId(selected.getLong("scheduleId"));
            convState.setHallName(selected.getStr("hallName"));
            convState.setShowDate(selected.getStr("showDate", convState.getShowDate()));
            convState.setStartTime(selected.getStr("startTime", convState.getStartTime()));
            stateManager.saveState(conversationId, convState);
            log.info("SearchSchedule 写回 scheduleId={}: conversationId={}",
                    convState.getScheduleId(), conversationId);
        } catch (Exception e) {
            log.warn("SearchSchedule 结果解析失败，跳过场次写回: conversationId={}", conversationId, e);
        }
    }

    private boolean sameTime(String requestedTime, String actualTime) {
        if (actualTime == null || actualTime.isBlank()) return false;
        try {
            return LocalTime.parse(requestedTime).equals(LocalTime.parse(actualTime));
        } catch (Exception ignored) {
            return requestedTime.equals(actualTime);
        }
    }

    /** 按指定字段匹配唯一场次（多个匹配返回 null，不写回避免误选） */
    private JSONObject matchUnique(JSONArray sessions, String field, String value) {
        JSONObject matched = null;
        for (int i = 0; i < sessions.size(); i++) {
            JSONObject session = sessions.getJSONObject(i);
            String v = session.getStr(field);
            if (v != null && (v.equals(value) || v.contains(value) || value.contains(v))) {
                if (matched != null) {
                    return null; // 多个匹配 → 不确定用户选哪个，不写回
                }
                matched = session;
            }
        }
        return matched;
    }

    private boolean has(String s) {
        return s != null && !s.isEmpty();
    }
}
