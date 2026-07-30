package com.limou.agent.ai;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 流式输出块
 * 支持文本块和工具调用事件两种类型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StreamChunk(
        /** 类型: text / tool_start */
        String type,
        /** 文本内容（type=text 时） */
        String content,
        /** 工具名称（type=tool_start 时） */
        String toolName,
        /** 工具显示名称（type=tool_start 时） */
        String toolDisplayName
) {
    public static StreamChunk text(String content) {
        return new StreamChunk("text", content, null, null);
    }

    public static StreamChunk toolStart(String toolName, String displayName) {
        return new StreamChunk("tool_start", null, toolName, displayName);
    }
}