package com.limou.agent.model.dto.movie;

/**
 * 卡片类型枚举
 * 定义电影票 Agent 支持的所有卡片类型
 */
public enum CardType {
    /** 影片列表卡片 */
    MOVIE_LIST("movie_list"),
    /** 场次列表卡片 */
    SESSION_LIST("session_list"),
    /** 座位图卡片 */
    SEAT_MAP("seat_map"),
    /** 订单确认卡片 */
    ORDER_CONFIRM("order_confirm"),
    /** 推荐卡片（替代方案、异常推荐） */
    RECOMMENDATION("recommendation"),
    /** 进度卡片 */
    PROGRESS("progress");

    private final String value;

    CardType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
