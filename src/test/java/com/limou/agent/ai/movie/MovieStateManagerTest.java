package com.limou.agent.ai.movie;

import com.limou.agent.model.dto.movie.ConversationState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovieStateManagerTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBucket<String> bucket;

    @InjectMocks
    private MovieStateManager stateManager;

    @BeforeEach
    void setUp() {
        when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);
    }

    @Test
    void keepsDateProvidedInCurrentTurnWhenClearingOldSchedule() {
        ConversationState existing = ConversationState.builder()
                .filmId(1L)
                .filmName("流浪地球3")
                .showDate("2026-08-24")
                .scheduleId(10L)
                .build();
        when(bucket.get()).thenReturn(existing.toJson());

        ConversationState newSlots = new ConversationState();
        newSlots.setShowDate("2026-08-25");
        newSlots.setStartTime("13:00");

        ConversationState merged = stateManager.mergeState("conversation-1", newSlots);

        assertThat(merged.getShowDate()).isEqualTo("2026-08-25");
        assertThat(merged.getStartTime()).isEqualTo("13:00");
        assertThat(merged.getScheduleId()).isNull();
    }
}
