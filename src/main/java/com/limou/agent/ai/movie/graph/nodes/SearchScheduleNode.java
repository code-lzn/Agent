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

        String result = tool.searchSchedules(
                convState.getFilmId(),
                convState.getCinemaId(),
                convState.getShowDate(),
                convState.getHallType());

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
