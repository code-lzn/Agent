package com.limou.agent.ai.movie.graph;

import cn.hutool.json.JSONUtil;
import com.limou.agent.model.dto.movie.ConversationState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Graph 工作流全局状态
 * 贯穿所有节点，是 StateGraph 的数据载体。
 * <p>
 * ConversationState 不再在每个节点各自从 Redis 读取，
 * 而是由 IntentClassifyNode 加载后挂到 graph state 上，逐节点透传。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovieGraphState implements Serializable {

    private static final long serialVersionUID = 1L;

    // ===== 输入 =====
    private String conversationId;
    private Long userId;
    private String userMessage;

    /**
     * SmartRouter 传递的预分类意图（非 null 时跳过 LLM 意图识别）
     */
    private GraphIntentResult preclassifiedIntent;

    // ===== GuardRail =====
    private boolean blocked;
    private String blockMessage;

    // ===== 意图识别 =====
    /** 识别出的意图，用于条件边路由 */
    private String intent;

    // ===== 会话状态（图内流转，不再每个节点各自读 Redis） =====
    /** IntentClassifyNode 从 Redis 加载并合并槽位后的最新状态 */
    private ConversationState convState;

    // ===== 工具执行 =====
    /** 工具执行结果 JSON */
    private String toolResult;
    /** 工具名称 */
    private String toolName;

    // ===== 响应生成 =====
    /** LLM 生成的最终回复 */
    private String response;

    /**
     * 获取会话状态 JSON（兼容旧接口）
     */
    public String getStateJson() {
        return convState != null ? convState.toJson() : null;
    }

    /**
     * 锁座结果路由 key（条件边用）：成功 → "success"（自动创建订单），失败 → "fail"（结束）。
     */
    public String lockRouteKey() {
        if (toolResult == null || toolResult.isBlank()) {
            return "fail";
        }
        try {
            return JSONUtil.parseObj(toolResult).getBool("success", false) ? "success" : "fail";
        } catch (Exception e) {
            return "fail";
        }
    }
}