package com.limou.agent.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

@Getter
public enum FilmStatusEnum {

    DRAFT("draft", "草稿"),
    PUBLISHED("published", "已发布"),
    OFFLINE("offline", "已下线"),
    // 新增两个状态
    NOW_SHOWING("now_showing", "正在热映"),   // 或 "showing"
    UPCOMING("upcoming", "即将上映");        // 或 "coming_soon"

    private final String value;
    private final String text;

    FilmStatusEnum(String value, String text) {
        this.value = value;
        this.text = text;
    }

    public static FilmStatusEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (FilmStatusEnum anEnum : FilmStatusEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}
