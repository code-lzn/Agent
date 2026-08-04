package com.limou.agent.model.dto.movie;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationStateTest {

    @Test
    void preservesScheduleAcrossJsonRoundTrip() {
        ConversationState state = ConversationState.builder()
                .conversationId("conversation-1")
                .filmId(11L)
                .cinemaId(12L)
                .scheduleId(22L)
                .lastUpdate(LocalDateTime.of(2026, 8, 25, 12, 30))
                .build();

        String json = state.toJson();
        ConversationState restored = ConversationState.fromJson(json);

        assertThat(json).isNotEqualTo("{}");
        assertThat(restored.getFilmId()).isEqualTo(11L);
        assertThat(restored.getCinemaId()).isEqualTo(12L);
        assertThat(restored.getScheduleId()).isEqualTo(22L);
        assertThat(restored.getLastUpdate()).isEqualTo(LocalDateTime.of(2026, 8, 25, 12, 30));
        assertThat(restored.getSeatIds()).isEmpty();
        assertThat(restored.getSeatLabels()).isEmpty();
    }
}
