package com.limou.agent.ai.movie.graph.nodes;

import com.limou.agent.ai.movie.MovieStateManager;
import com.limou.agent.ai.movie.graph.MovieGraphState;
import com.limou.agent.ai.movie.tools.SearchCinemasTool;
import com.limou.agent.ai.movie.tools.SearchFilmsTool;
import com.limou.agent.model.dto.movie.ConversationState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchSelectionNodeTest {

    @Test
    void persistsExactFilmResult() {
        SearchFilmsTool tool = mock(SearchFilmsTool.class);
        MovieStateManager stateManager = mock(MovieStateManager.class);
        SearchFilmNode node = new SearchFilmNode(tool, stateManager);
        ConversationState conversation = ConversationState.builder().filmName("流浪地球3").build();
        MovieGraphState state = MovieGraphState.builder()
                .conversationId("conversation-1").convState(conversation).build();
        when(tool.searchFilms("流浪地球3", null, "rating_desc"))
                .thenReturn("{\"films\":[{\"filmId\":11,\"name\":\"流浪地球3\"}]}");

        node.execute(state);

        assertThat(conversation.getFilmId()).isEqualTo(11L);
        verify(stateManager).saveState("conversation-1", conversation);
    }

    @Test
    void persistsExactCinemaResult() {
        SearchCinemasTool tool = mock(SearchCinemasTool.class);
        MovieStateManager stateManager = mock(MovieStateManager.class);
        SearchCinemaNode node = new SearchCinemaNode(tool, stateManager);
        ConversationState conversation = ConversationState.builder()
                .filmId(11L)
                .cinemaName("洛阳万达影城(泉舜店)")
                .currentCity("洛阳")
                .build();
        MovieGraphState state = MovieGraphState.builder()
                .conversationId("conversation-1").convState(conversation).build();
        when(tool.searchCinemas("洛阳万达影城(泉舜店)", "洛阳", 11L))
                .thenReturn("{\"cinemas\":[{\"cinemaId\":12,\"name\":\"洛阳万达影城(泉舜店)\"}]}");

        node.execute(state);

        assertThat(conversation.getCinemaId()).isEqualTo(12L);
        verify(stateManager).saveState("conversation-1", conversation);
    }
}
