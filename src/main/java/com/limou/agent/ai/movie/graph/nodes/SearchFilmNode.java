package com.limou.agent.ai.movie.graph.nodes;

import com.limou.agent.ai.graph.GraphNode;
import com.limou.agent.ai.movie.graph.MovieGraphState;
import com.limou.agent.ai.movie.graph.MovieIntent;
import com.limou.agent.ai.movie.tools.SearchFilmsTool;
import com.limou.agent.model.dto.movie.ConversationState;
import lombok.extern.slf4j.Slf4j;

/**
 * 搜索影片节点
 */
@Slf4j
public class SearchFilmNode implements GraphNode<MovieGraphState> {

    private final SearchFilmsTool tool;

    public SearchFilmNode(SearchFilmsTool tool) {
        this.tool = tool;
    }

    @Override
    public MovieGraphState execute(MovieGraphState state) {
        if (state.isBlocked()) {
            return state;
        }

        ConversationState convState = state.getConvState();

        String result = tool.searchFilms(
                convState.getFilmName(),
                convState.getFilmType(),
                "rating_desc");

        state.setToolResult(result);
        state.setToolName(MovieIntent.SEARCH_MOVIE.getCode());
        log.info("SearchFilm 完成: conversationId={}", state.getConversationId());
        return state;
    }
}