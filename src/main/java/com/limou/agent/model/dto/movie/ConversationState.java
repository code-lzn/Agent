package com.limou.agent.model.dto.movie;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 电影票对话状态
 * 追踪当前会话的槽位填充进度和已收集信息
 * 存储在 Redis 中，key: movie:state:{conversationId}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConversationState implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    /** 会话ID */
    private String conversationId;

    /** 用户ID */
    private Long userId;

    /** 当前步骤: film_selection / cinema_selection / showtime_selection / seat_selection / order_confirmation */
    private String currentStep;

    /** 已完成的步骤列表 */
    @Builder.Default
    private List<String> completedSteps = new ArrayList<>();

    // ===== 槽位 =====

    /** 影片ID */
    private Long filmId;

    /** 影片名称 */
    private String filmName;

    /** 影片类型（喜剧/动作/科幻等） */
    private String filmType;

    /** 影院ID */
    private Long cinemaId;

    /** 影院名称 */
    private String cinemaName;

    /** 位置标签（公司附近/家附近等） */
    private String locationTag;

    /** 前端定位或手动选择的当前城市 */
    private String currentCity;

    /** 用户当前纬度（WGS84），来自前端 GPS/IP 定位 */
    private Double userLat;

    /** 用户当前经度（WGS84），来自前端 GPS/IP 定位 */
    private Double userLng;

    /** 厅型偏好（IMAX/杜比/普通/4DX/VIP） */
    private String hallType;

    /** 放映日期（yyyy-MM-dd） */
    private String showDate;

    /** 开场时间（HH:mm） */
    private String startTime;

    /** 场次ID */
    private Long scheduleId;

    /** 影厅名称 */
    private String hallName;

    /** 购票数量 */
    private Integer ticketCount;

    /** 单张票价预算上限 */
    private BigDecimal budgetMax;

    /** 已锁定座位ID列表 */
    @Builder.Default
    private List<Long> seatIds = new ArrayList<>();

    /** 已锁定座位标签列表（如 "5排6座"） */
    @Builder.Default
    private List<String> seatLabels = new ArrayList<>();

    /** 订单总价 */
    private BigDecimal totalPrice;

    /** 订单ID */
    private Long orderId;

    /** 偏好座位区域（中间/靠前/靠后/靠边） */
    private String preferredSeatZone;

    /** 是否已加载用户偏好 */
    private boolean preferencesLoaded;

    /** 上次搜索结果（用于指代消解："第二个"、"便宜的"） */
    private String lastSearchContext;

    /** 上次更新时间 */
    private LocalDateTime lastUpdate;

    // ===== 辅助方法 =====

    /**
     * 检查指定槽位是否已填充
     */
    public boolean isSlotFilled(String slotKey) {
        return switch (slotKey) {
            case "film" -> filmId != null && filmName != null;
            case "cinema" -> cinemaId != null && cinemaName != null;
            case "schedule" -> scheduleId != null;
            case "time" -> showDate != null && startTime != null;
            case "count" -> ticketCount != null && ticketCount > 0;
            case "hallType" -> hallType != null && !hallType.isEmpty();
            case "seats" -> seatIds != null && !seatIds.isEmpty();
            case "price" -> budgetMax != null;
            default -> false;
        };
    }

    /**
     * 获取所有缺失的槽位（按追问优先级排序）
     * 优先级: film > time > count > cinema > hall > price
     */
    public List<String> getMissingSlots() {
        List<String> missing = new ArrayList<>();
        if (!isSlotFilled("film")) missing.add("film");
        if (!isSlotFilled("time")) missing.add("time");
        if (!isSlotFilled("count")) missing.add("count");
        if (!isSlotFilled("cinema")) missing.add("cinema");
        if (!isSlotFilled("hallType")) missing.add("hallType");
        if (!isSlotFilled("price")) missing.add("price");
        if (!isSlotFilled("schedule")) missing.add("schedule");
        return missing;
    }

    /**
     * 序列化为 JSON
     */
    public String toJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    /**
     * 从 JSON 反序列化
     */
    public static ConversationState fromJson(String json) {
        try {
            ConversationState state = OBJECT_MAPPER.readValue(json, ConversationState.class);
            if (state.completedSteps == null) state.completedSteps = new ArrayList<>();
            if (state.seatIds == null) state.seatIds = new ArrayList<>();
            if (state.seatLabels == null) state.seatLabels = new ArrayList<>();
            return state;
        } catch (JsonProcessingException e) {
            return new ConversationState();
        }
    }

    /**
     * 获取当前状态的文本摘要（注入到 prompt 中）
     */
    public String toPromptContext() {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("【当前对话状态】\n");
        sb.append("当前步骤: ").append(currentStep != null ? currentStep : "初始").append("\n");

        if (filmName != null) {
            sb.append("已选影片: 《").append(filmName).append("》");
            if (filmType != null) sb.append("（").append(filmType).append("）");
            if (hallType != null) sb.append("，偏好厅型: ").append(hallType);
            sb.append("\n");
        }
        if (cinemaName != null) {
            sb.append("已选影院: ").append(cinemaName).append("\n");
        }
        if (currentCity != null) {
            sb.append("当前城市: ").append(currentCity).append("\n");
        }
        if (userLat != null && userLng != null && userLat != 0 && userLng != 0) {
            sb.append("用户精确坐标: lat=").append(userLat).append(", lng=").append(userLng)
              .append("（调用 searchNearbyCinemas 时务必传入此坐标以获得精准附近结果）\n");
        }
        if (showDate != null) {
            sb.append("日期: ").append(showDate);
            if (startTime != null) sb.append(" ").append(startTime);
            sb.append("\n");
        }
        if (ticketCount != null) {
            sb.append("票数: ").append(ticketCount).append("张\n");
        }
        if (scheduleId != null) {
            sb.append("已选场次ID: ").append(scheduleId);
            if (hallName != null) sb.append("（").append(hallName).append("）");
            sb.append("\n");
        }
        if (seatLabels != null && !seatLabels.isEmpty()) {
            sb.append("已选座位: ").append(String.join("、", seatLabels)).append("\n");
        }
        if (orderId != null) {
            sb.append("当前订单ID: ").append(orderId).append("\n");
        }
        if (totalPrice != null) {
            sb.append("总价: ¥").append(totalPrice).append("\n");
        }
        if (budgetMax != null) {
            sb.append("预算上限: ¥").append(budgetMax).append("/张\n");
        }

        List<String> missing = getMissingSlots();
        if (!missing.isEmpty()) {
            sb.append("尚缺信息: ").append(String.join(" > ", missing)).append("\n");
        } else {
            sb.append("状态: 信息齐全，可直接下单\n");
        }

        // 最近搜索结果摘要（卡片数据），供后续轮次引用（如"第二个场次"、"这个场次是哪个影院"）
        if (lastSearchContext != null && !lastSearchContext.isBlank()) {
            String ctx = lastSearchContext.length() > 400
                    ? lastSearchContext.substring(0, 400) + "…"
                    : lastSearchContext;
            sb.append("最近搜索结果摘要: ").append(ctx).append("\n");
        }

        sb.append("---\n");
        return sb.toString();
    }
}
