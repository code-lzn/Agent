package com.limou.agent.ai.movie.graph;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 电影票 Agent 意图枚举
 * <p>
 * 替代项目中硬编码的 intent 字符串（"search_movie" 等），
 * 提供类型安全的意图判断和工具路由能力。
 */
public enum MovieIntent {

    // ===== 工具意图（需要调用具体工具） =====
    SEARCH_MOVIE("search_movie", "搜索影片"),
    SEARCH_CINEMA("search_cinema", "搜索影院"),
    SEARCH_NEARBY("search_nearby", "搜索附近影院"),
    SEARCH_SCHEDULE("search_schedule", "搜索场次"),
    GET_SEAT_MAP("get_seat_map", "获取座位图"),
    LOCK_SEATS("lock_seats", "锁定座位"),
    CREATE_ORDER("create_order", "创建订单"),
    PAY_ORDER("pay_order", "支付订单"),
    GET_PREFERENCE("get_preference", "获取偏好"),

    // ===== 非工具意图（纯 LLM 回复） =====
    GREETING("greeting", "问候"),
    CHAT("chat", "闲聊"),
    UNKNOWN("unknown", "未知");

    /** 意图编码（与 LLM prompt 中定义的 intent 值一致） */
    private final String code;

    /** 中文显示名称 */
    private final String displayName;

    private static final Map<String, MovieIntent> CODE_MAP;

    static {
        Map<String, MovieIntent> map = new LinkedHashMap<>();
        for (MovieIntent intent : values()) {
            map.put(intent.code, intent);
        }
        CODE_MAP = Collections.unmodifiableMap(map);
    }

    MovieIntent(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 根据编码查找意图
     *
     * @param code 意图编码字符串
     * @return 匹配的枚举，未匹配时返回 {@link #UNKNOWN}
     */
    public static MovieIntent fromCode(String code) {
        if (code == null || code.isBlank()) {
            return UNKNOWN;
        }
        return CODE_MAP.getOrDefault(code, UNKNOWN);
    }

    /**
     * 获取所有工具意图的编码→中文名映射
     * <p>
     * 用于前端展示 "正在搜索影片..." 等状态文字
     */
    public static Map<String, String> toolDisplayNames() {
        return Stream.of(values())
                .filter(MovieIntent::isToolIntent)
                .collect(Collectors.toMap(
                        MovieIntent::getCode,
                        MovieIntent::getDisplayName,
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    /**
     * 是否为需要调用工具的意图
     */
    public boolean isToolIntent() {
        return switch (this) {
            case GREETING, CHAT, UNKNOWN -> false;
            default -> true;
        };
    }
}
