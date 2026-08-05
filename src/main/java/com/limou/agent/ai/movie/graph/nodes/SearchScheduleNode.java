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

        // ★ 安全网：cinemaId 未解析时不能查场次（用户可能没选影院，查全城场次体验差）
        if (convState.getCinemaId() == null && convState.getCinemaName() == null) {
            String error = "{\"error\":\"请先选择影院\",\"diagnosis\":\"cinema_required\",\"hint\":\"用户尚未指定影院，请引导用户选择观影城市和影院后再查场次\"}";
            state.setToolResult(error);
            state.setToolName(MovieIntent.SEARCH_SCHEDULE.getCode());
            log.warn("SearchSchedule 被阻止: cinemaId=null, cinemaName=null, conversationId={}",
                    state.getConversationId());
            return state;
        }

        // ★ 安全网：有影院名但缺 cinemaId，提示先查影院
        if (convState.getCinemaId() == null && convState.getCinemaName() != null) {
            String error = "{\"error\":\"影院ID缺失，请先调用 searchCinemas 获取影院ID\",\"cinemaName\":\""
                    + convState.getCinemaName() + "\",\"diagnosis\":\"cinema_id_missing\"}";
            state.setToolResult(error);
            state.setToolName(MovieIntent.SEARCH_SCHEDULE.getCode());
            log.warn("SearchSchedule 被阻止: cinemaId=null, cinemaName={}, conversationId={}",
                    convState.getCinemaName(), state.getConversationId());
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
            } else if (sessions.size() == 1) {
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
}
