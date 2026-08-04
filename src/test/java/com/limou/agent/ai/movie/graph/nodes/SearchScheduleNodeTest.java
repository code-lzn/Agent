package com.limou.agent.ai.movie.graph.nodes;

import com.limou.agent.ai.movie.MovieStateManager;
import com.limou.agent.ai.movie.graph.MovieGraphState;
import com.limou.agent.ai.movie.tools.SearchSchedulesTool;
import com.limou.agent.model.dto.movie.ConversationState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchScheduleNodeTest {

    @Test
    void persistsScheduleThatUniquelyMatchesRequestedTime() {
        SearchSchedulesTool tool = mock(SearchSchedulesTool.class);
        MovieStateManager stateManager = mock(MovieStateManager.class);
        SearchScheduleNode node = new SearchScheduleNode(tool, stateManager);
        ConversationState conversation = ConversationState.builder()
                .filmId(1L)
                .cinemaId(2L)
                .showDate("2026-08-25")
                .startTime("13:00")
                .build();
        MovieGraphState state = MovieGraphState.builder()
                .conversationId("conversation-1")
                .convState(conversation)
                .build();
        String result = "{\"sessions\":["
                + "{\"scheduleId\":41,\"showDate\":\"2026-08-25\",\"startTime\":\"10:00:00\",\"hallName\":\"1号厅\"},"
                + "{\"scheduleId\":42,\"showDate\":\"2026-08-25\",\"startTime\":\"13:00:00\",\"hallName\":\"杜比全景声厅\"}]}";
        when(tool.searchSchedules(1L, 2L, "2026-08-25", null)).thenReturn(result);

        node.execute(state);

        assertThat(conversation.getScheduleId()).isEqualTo(42L);
        assertThat(conversation.getHallName()).isEqualTo("杜比全景声厅");
        assertThat(conversation.getStartTime()).isEqualTo("13:00:00");
        verify(stateManager).saveState("conversation-1", conversation);
    }
}
