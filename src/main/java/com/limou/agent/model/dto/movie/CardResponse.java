package com.limou.agent.model.dto.movie;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 卡片响应 — Agent 返回给前端的统一消息格式
 * 支持纯文本和卡片两种类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CardResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息类型: "card" 或 "text" */
    private String type;

    /** 卡片类型（type="card"时必填） */
    private String cardType;

    /** 卡片数据（type="card"时必填） */
    private Map<String, Object> data;

    /** 文本回复（始终有值） */
    private String text;

    /** 当前槽位状态（可选，用于前端展示进度） */
    private ConversationState slots;

    /**
     * 创建纯文本响应
     */
    public static CardResponse text(String message) {
        return CardResponse.builder()
                .type("text")
                .text(message)
                .build();
    }

    /**
     * 创建卡片响应
     */
    public static CardResponse card(CardType cardType, Map<String, Object> data, String text) {
        return CardResponse.builder()
                .type("card")
                .cardType(cardType.getValue())
                .data(data)
                .text(text)
                .build();
    }

    /**
     * 创建带槽位状态的卡片响应
     */
    public static CardResponse cardWithState(CardType cardType, Map<String, Object> data,
                                              String text, ConversationState state) {
        return CardResponse.builder()
                .type("card")
                .cardType(cardType.getValue())
                .data(data)
                .text(text)
                .slots(state)
                .build();
    }
}
