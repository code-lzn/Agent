package com.limou.agent.ai.movie.graph.nodes;

import com.limou.agent.ai.graph.GraphNode;
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

    public SearchCinemaNode(SearchCinemasTool tool) {
        this.tool = tool;
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
        log.info("SearchCinema 完成: conversationId={}", state.getConversationId());
        return state;
    }
}