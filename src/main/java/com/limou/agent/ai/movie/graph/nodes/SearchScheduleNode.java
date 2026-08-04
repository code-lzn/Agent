package com.limou.agent.ai.movie.graph.nodes;

import com.limou.agent.ai.graph.GraphNode;
import com.limou.agent.ai.movie.graph.MovieGraphState;
import com.limou.agent.ai.movie.graph.MovieIntent;
import com.limou.agent.ai.movie.tools.SearchSchedulesTool;
import com.limou.agent.model.dto.movie.ConversationState;
import lombok.extern.slf4j.Slf4j;

/**
 * 搜索场次节点
 */
@Slf4j
public class SearchScheduleNode implements GraphNode<MovieGraphState> {

    private final SearchSchedulesTool tool;

    public SearchScheduleNode(SearchSchedulesTool tool) {
        this.tool = tool;
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
        log.info("SearchSchedule 完成: conversationId={}", state.getConversationId());
        return state;
    }
}