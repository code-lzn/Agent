package com.limou.agent.ai.movie.graph.nodes;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.limou.agent.ai.graph.GraphNode;
import com.limou.agent.ai.movie.MovieStateManager;
import com.limou.agent.ai.movie.graph.MovieGraphState;
import com.limou.agent.ai.movie.graph.MovieIntent;
import com.limou.agent.ai.movie.tools.SearchCinemasTool;
import com.limou.agent.model.dto.movie.ConversationState;
import lombok.extern.slf4j.Slf4j;

/**
 * 搜索影院节点
 */
@Slf4j
public class SearchCinemaNode implements GraphNode<MovieGraphState> {

    private final SearchCinemasTool tool;
    private final MovieStateManager stateManager;

    public SearchCinemaNode(SearchCinemasTool tool, MovieStateManager stateManager) {
        this.tool = tool;
        this.stateManager = stateManager;
    }

    @Override
    public MovieGraphState execute(MovieGraphState state) {
        if (state.isBlocked()) {
            return state;
        }

        ConversationState convState = state.getConvState();

        String result = tool.searchCinemas(
                convState.getCinemaName(),
                convState.getCurrentCity(),
                convState.getFilmId());

        state.setToolResult(result);
        state.setToolName(MovieIntent.SEARCH_CINEMA.getCode());
        persistResolvedCinema(result, convState, state.getConversationId());
        log.info("SearchCinema 完成: conversationId={}", state.getConversationId());
        return state;
    }

    private void persistResolvedCinema(String result, ConversationState convState, String conversationId) {
        try {
            JSONArray cinemas = JSONUtil.parseObj(result).getJSONArray("cinemas");
            if (cinemas == null || cinemas.isEmpty()) return;

            JSONObject selected = null;
            for (int i = 0; i < cinemas.size(); i++) {
                JSONObject cinema = cinemas.getJSONObject(i);
                if (convState.getCinemaName() != null
                        && convState.getCinemaName().equalsIgnoreCase(cinema.getStr("name"))) {
                    selected = cinema;
                    break;
                }
            }
            if (selected == null && cinemas.size() == 1) selected = cinemas.getJSONObject(0);
            if (selected == null || selected.getLong("cinemaId") == null) return;

            convState.setCinemaId(selected.getLong("cinemaId"));
            convState.setCinemaName(selected.getStr("name", convState.getCinemaName()));
            stateManager.saveState(conversationId, convState);
            log.info("SearchCinema 写回 cinemaId={}: conversationId={}", convState.getCinemaId(), conversationId);
        } catch (Exception e) {
            log.warn("SearchCinema 结果解析失败，跳过影院写回: conversationId={}", conversationId, e);
        }
    }
}
