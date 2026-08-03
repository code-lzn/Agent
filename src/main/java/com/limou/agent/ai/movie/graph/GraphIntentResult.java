package com.limou.agent.ai.movie.graph;

import com.limou.agent.model.dto.movie.ConversationState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * LLM 意图识别 + 槽位提取结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphIntentResult {
    /** 意图: search_movie / search_cinema / search_schedule / get_seat_map / lock_seats / create_order / pay_order / get_preference / greeting / chat / unknown */
    private String intent;
    /** 提取的槽位（会合并到 ConversationState） */
    private ConversationState slots;
    /** 建议的追问文本（信息不足时） */
    private String askPrompt;
}