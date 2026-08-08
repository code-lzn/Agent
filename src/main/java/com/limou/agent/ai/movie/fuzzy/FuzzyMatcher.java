package com.limou.agent.ai.movie.fuzzy;

import org.springframework.stereotype.Component;

/**
 * 模糊匹配评分器（纯函数，可单测）。
 * 拼音相似度评分 0-100，阈值 58，对齐知识库 7.2 分级（&gt;90 静默 / 50-90 确认 / &lt;50 追问）。
 */
@Component
public class FuzzyMatcher {

    /** 命中最低阈值：对应知识库「置信度 50% 附近」，低于此值视为不命中 */
    public static final int MATCH_THRESHOLD = 58;

    /**
     * 经典 Levenshtein 编辑距离。
     */
    public int levenshtein(String a, String b) {
        if (a == null || a.isEmpty()) {
            return b == null ? 0 : b.length();
        }
        if (b == null || b.isEmpty()) {
            return a.length();
        }
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(prev[j] + 1, curr[j - 1] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }

    /**
     * 相似度 0.0-1.0（1 - dist/maxLen）。
     */
    public double similarity(String a, String b) {
        if (a == null || b == null) {
            return 0.0;
        }
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) {
            return 1.0;
        }
        return 1.0 - (double) levenshtein(a, b) / maxLen;
    }

    /**
     * 拼音 key 相似度评分 0-100：
     * <ul>
     *   <li>相等 → 100（同音词主路径）</li>
     *   <li>前缀（用户 key ≥4 字符）→ 90（"zhizhuxia" → "zhizhuxiazhanxinzhiri"）</li>
     *   <li>前缀（短词）→ 82</li>
     *   <li>包含（双 minLen ≥4）→ 76</li>
     *   <li>编辑距离相似度 ≥0.9 → 88 / ≥0.8 → 72 / ≥0.7 → 58（平翘舌、前后鼻音）</li>
     * </ul>
     */
    public int scorePinyin(String userKey, String candKey) {
        if (userKey == null || candKey == null || userKey.isEmpty() || candKey.isEmpty()) {
            return 0;
        }
        if (userKey.equals(candKey)) {
            return 100;
        }
        if (candKey.startsWith(userKey)) {
            return userKey.length() >= 4 ? 90 : 82;
        }
        if (userKey.startsWith(candKey)) {
            return candKey.length() >= 4 ? 82 : 76;
        }
        if (candKey.contains(userKey) || userKey.contains(candKey)) {
            int minLen = Math.min(userKey.length(), candKey.length());
            if (minLen >= 4) {
                return 76;
            }
        }
        double sim = similarity(userKey, candKey);
        if (sim >= 0.9) {
            return 88;
        }
        if (sim >= 0.8) {
            return 72;
        }
        if (sim >= 0.7) {
            return 58;
        }
        return 0;
    }
}
