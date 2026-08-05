package com.limou.agent.ai;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 流式输出块
 * 支持文本块、工具调用事件和卡片事件
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StreamChunk(
        /** 类型: text / tool_start / card */
        String type,
        /** 文本内容（type=text 时） */
        String content,
        /** 工具名称（type=tool_start 时） */
        String toolName,
        /** 工具显示名称（type=tool_start 时） */
        String toolDisplayName,
        /** 卡片类型（type=card 时） */
        String cardType,
        /** 卡片数据 JSON 字符串（type=card 时） */
        String cardData
) {
    public static StreamChunk text(String content) {
        return new StreamChunk("text", content, null, null, null, null);
    }

    public static StreamChunk toolStart(String toolName, String displayName) {
        return new StreamChunk("tool_start", null, toolName, displayName, null, null);
    }

    public static StreamChunk card(String cardType, String cardData) {
        return new StreamChunk("card", null, null, null, cardType, cardData);
    }
}