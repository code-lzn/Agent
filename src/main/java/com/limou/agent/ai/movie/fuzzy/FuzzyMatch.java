package com.limou.agent.ai.movie.fuzzy;

/**
 * 模糊匹配结果。
 *
 * @param rawInput     用户原词
 * @param matchedName  纠错后的标准名（DB 权威名）
 * @param matchedId    影片/影院 ID；厅型归一化为 null
 * @param confidence   置信度 0-100，对齐知识库 7.2 分级（&gt;90 静默 / 50-90 确认 / &lt;50 追问）
 * @param source       命中来源
 */
public record FuzzyMatch(
        String rawInput,
        String matchedName,
        Long matchedId,
        int confidence,
        Source source
) {

    /**
     * 命中来源
     */
    public enum Source {
        /** 知识库别名表命中 */
        ALIAS,
        /** 拼音完全相等 */
        PINYIN_EXACT,
        /** 拼音前缀 */
        PINYIN_PREFIX,
        /** 拼音包含 */
        PINYIN_CONTAINS,
        /** 拼音编辑距离 */
        PINYIN_EDIT,
        /** 英文名匹配 */
        ENGLISH
    }
}
