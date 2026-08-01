package com.limou.agent.ai.movie.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Graph 工作流全局状态
 * 贯穿所有节点，是 StateGraph 的数据载体
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

    // ===== GuardRail =====
    private boolean blocked;
    private String blockMessage;

    // ===== 意图识别 =====
    /** 识别出的意图: search_movie / search_cinema / search_schedule / get_seat_map / lock_seats / create_order / pay_order / greeting / unknown */
    private String intent;
    /** 提取的槽位 */
    private Map<String, Object> extractedSlots;

    // ===== 工具执行 =====
    /** 工具执行结果 JSON */
    private String toolResult;
    /** 工具名称 */
    private String toolName;

    // ===== 响应生成 =====
    /** LLM 生成的最终回复 */
    private String response;

    // ===== 当前对话状态（槽位累计） =====
    private String currentStep;
    private String filmName;
    private Long filmId;
    private String cinemaName;
    private Long cinemaId;
    private String showDate;
    private String startTime;
    private Long scheduleId;
    private String hallName;
    private String hallType;
    private Integer ticketCount;
    private java.util.List<Long> seatIds;
    private Long orderId;
    private String stateJson;

    public Map<String, Object> getOrCreateSlots() {
        if (extractedSlots == null) {
            extractedSlots = new LinkedHashMap<>();
        }
        return extractedSlots;
    }
}