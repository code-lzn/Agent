package com.limou.agent.ai.movie.graph;

import com.limou.agent.model.dto.movie.ConversationState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmartMovieRouterTest {

    @Mock
    private GraphIntentClassifier intentClassifier;

    @InjectMocks
    private SmartMovieRouter router;

    @Test
    void routesGreetingWithoutCallingClassifier() {
        SmartRouteResult result = router.route("你好", new ConversationState());

        assertThat(result.decision()).isEqualTo(RouterDecision.GRAPH);
        assertThat(result.intentResult().getIntent()).isEqualTo("greeting");
        verifyNoInteractions(intentClassifier);
    }

    @Test
    void routesNearbyCinemaWithoutCallingClassifier() {
        SmartRouteResult result = router.route("附近有哪些影院", new ConversationState());

        assertThat(result.decision()).isEqualTo(RouterDecision.GRAPH);
        assertThat(result.intentResult().getIntent()).isEqualTo("search_cinema");
        verifyNoInteractions(intentClassifier);
    }

    @Test
    void routesCompleteBookingDirectlyToReact() {
        SmartRouteResult result = router.route("买《哪吒》明天晚上两张票", new ConversationState());

        assertThat(result.decision()).isEqualTo(RouterDecision.REACT);
        assertThat(result.intentResult()).isNull();
        verifyNoInteractions(intentClassifier);
    }

    @Test
    void reusesClassifierResultForGraphRoute() {
        ConversationState state = new ConversationState();
        GraphIntentResult classified = GraphIntentResult.builder()
                .intent("search_schedule")
                .slots(new ConversationState())
                .build();
        when(intentClassifier.classify("帮我看看场次", state)).thenReturn(classified);

        SmartRouteResult result = router.route("帮我看看场次", state);

        assertThat(result.decision()).isEqualTo(RouterDecision.GRAPH);
        assertThat(result.intentResult()).isSameAs(classified);
    }
}
