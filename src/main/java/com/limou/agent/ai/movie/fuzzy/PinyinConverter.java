package com.limou.agent.ai.movie.fuzzy;

import com.github.promeg.pinyinhelper.Pinyin;
import com.github.promeg.tinypinyin.lexicons.java.cncity.CnCityDict;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 拼音转换器：封装 TinyPinyin（无声调、返回大写）。
 * 提供输入规范化 + 拼音 key 生成，供模糊匹配索引使用。
 */
@Slf4j
@Component
public class PinyinConverter {

    /**
     * 初始化 TinyPinyin，挂载城市多音字词典（如 重庆→chongqing）。
     */
    @PostConstruct
    public void init() {
        try {
            Pinyin.init(Pinyin.newConfig().with(CnCityDict.getInstance()));
            log.info("PinyinConverter 初始化完成（含城市词典）");
        } catch (Exception e) {
            log.warn("TinyPinyin 初始化失败，拼音纠错可能不完整: {}", e.getMessage());
        }
    }

    /**
     * 输入规范化：trim + 全角→半角 + 间隔号统一为 '·'。
     */
    public String normalize(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        StringBuilder sb = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            sb.append(normalizeChar(c));
        }
        return sb.toString();
    }

    private char normalizeChar(char c) {
        // 全角字母数字/符号 → 半角（U+FF01..U+FF5E → U+0021..U+007E）
        if (c >= '！' && c <= '～') {
            return (char) (c - 0xFEE0);
        }
        // 全角空格 → 半角空格
        if (c == '　') {
            return ' ';
        }
        // 多种间隔号统一为 '·'（中文间隔号 U+00B7）
        if (c == '•' || c == '‧' || c == '･' || c == '・') {
            return '·';
        }
        return c;
    }

    /**
     * 生成拼音 key：normalize → toPinyin（逐字，大写无声调）→ 去除非 [a-z0-9] → 小写。
     * 例："支柱下" → "zhizhuxia"，"蜘蛛侠·崭新之日" → "zhizhuxiazhanxinzhiri"。
     * 间隔号/空格/标点在 key 中被剥离，因此「熊出没·逆转时空」与「熊出没逆转时空」同 key。
     */
    public String toPinyinKey(String s) {
        String normalized = normalize(s);
        if (normalized.isEmpty()) {
            return "";
        }
        String pinyin = Pinyin.toPinyin(normalized, "");
        StringBuilder sb = new StringBuilder(pinyin.length());
        for (int i = 0; i < pinyin.length(); i++) {
            char c = pinyin.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            }
        }
        return sb.toString().toLowerCase();
    }
}
