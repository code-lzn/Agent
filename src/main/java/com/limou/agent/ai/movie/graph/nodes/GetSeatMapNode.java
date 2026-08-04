package com.limou.agent.ai.movie.graph.nodes;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.limou.agent.ai.graph.GraphNode;
import com.limou.agent.ai.movie.graph.MovieGraphState;
import com.limou.agent.ai.movie.graph.MovieIntent;
import com.limou.agent.ai.movie.tools.GetSeatMapTool;
import com.limou.agent.model.dto.movie.ConversationState;
import lombok.extern.slf4j.Slf4j;

/**
 * 获取座位图节点
 */
@Slf4j
public class GetSeatMapNode implements GraphNode<MovieGraphState> {

    private final GetSeatMapTool tool;

    public GetSeatMapNode(GetSeatMapTool tool) {
        this.tool = tool;
    }

    @Override
    public MovieGraphState execute(MovieGraphState state) {
        if (state.isBlocked()) {
            return state;
        }

        ConversationState convState = state.getConvState();

        if (convState.getScheduleId() == null) {
            JSONObject error = JSONUtil.createObj().set("error", "请先选择场次");
            if (convState.getFilmId() != null) error.set("filmId", convState.getFilmId());
            if (convState.getCinemaId() != null) error.set("cinemaId", convState.getCinemaId());
            state.setToolResult(error.toString());
            state.setToolName(MovieIntent.GET_SEAT_MAP.getCode());
            return state;
        }

        String result = tool.getSeatMap(convState.getScheduleId());
        state.setToolResult(result);
        state.setToolName(MovieIntent.GET_SEAT_MAP.getCode());
        log.info("GetSeatMap 完成: conversationId={}", state.getConversationId());
        return state;
    }
}
