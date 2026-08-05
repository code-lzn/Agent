package com.limou.agent.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.limou.agent.model.entity.ChatHistory;
import com.limou.agent.service.ChatHistoryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AiServiceImplHistoryTest {

    @Test
    void persistsCardBetweenUserAndAssistantMessages() {
        ChatHistoryService historyService = mock(ChatHistoryService.class);
        AiServiceImpl aiService = new AiServiceImpl();
        ReflectionTestUtils.setField(aiService, "chatHistoryService", historyService);

        ReflectionTestUtils.invokeMethod(
                aiService,
                "saveMovieChatHistory",
                "77",
                9L,
                "查看场次",
                "已经为你找到场次",
                "schedule_list",
                Map.of("sessions", List.of(Map.of("scheduleId", 42L))));

        ArgumentCaptor<ChatHistory> captor = ArgumentCaptor.forClass(ChatHistory.class);
        verify(historyService, times(3)).save(captor.capture());
        List<ChatHistory> saved = captor.getAllValues();

        assertThat(saved).extracting(ChatHistory::getMessageType)
                .containsExactly("user", "card", "ai");
        JSONObject card = JSONUtil.parseObj(saved.get(1).getMessage());
        assertThat(card.getStr("type")).isEqualTo("card");
        assertThat(card.getStr("cardType")).isEqualTo("schedule_list");
        assertThat(saved.get(2).getMessage()).isEqualTo("已经为你找到场次");
    }
}
