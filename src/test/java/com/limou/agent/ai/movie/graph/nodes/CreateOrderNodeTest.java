package com.limou.agent.ai.movie.graph.nodes;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.limou.agent.ai.movie.MovieStateManager;
import com.limou.agent.ai.movie.graph.MovieGraphState;
import com.limou.agent.ai.movie.tools.CreateOrderTool;
import com.limou.agent.model.dto.movie.ConversationState;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CreateOrderNodeTest {

    @Test
    void returnsRecoverableErrorWhenScheduleIsMissing() {
        CreateOrderNode node = new CreateOrderNode(
                mock(CreateOrderTool.class), mock(MovieStateManager.class));
        ConversationState conversation = ConversationState.builder()
                .filmId(11L)
                .cinemaId(12L)
                .build();
        MovieGraphState state = MovieGraphState.builder()
                .conversationId("conversation-1")
                .convState(conversation)
                .build();

        node.execute(state);

        JSONObject result = JSONUtil.parseObj(state.getToolResult());
        assertThat(result.getBool("success")).isFalse();
        assertThat(result.getStr("error")).isEqualTo("请先选择场次");
        assertThat(result.getLong("filmId")).isEqualTo(11L);
        assertThat(result.getLong("cinemaId")).isEqualTo(12L);
    }

    @Test
    void returnsSeatSelectionRouteWhenSeatsAreMissing() {
        CreateOrderNode node = new CreateOrderNode(
                mock(CreateOrderTool.class), mock(MovieStateManager.class));
        ConversationState conversation = ConversationState.builder()
                .scheduleId(42L)
                .seatIds(Collections.emptyList())
                .build();
        MovieGraphState state = MovieGraphState.builder()
                .conversationId("conversation-1")
                .convState(conversation)
                .build();

        node.execute(state);

        JSONObject result = JSONUtil.parseObj(state.getToolResult());
        assertThat(result.getBool("success")).isFalse();
        assertThat(result.getStr("error")).isEqualTo("请先选择并锁定座位");
        assertThat(result.getLong("scheduleId")).isEqualTo(42L);
    }
}
