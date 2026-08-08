package com.limou.agent.ai.movie.fuzzy;

import com.limou.agent.mapper.FilmMapper;
import com.limou.agent.model.entity.Film;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 影片拼音索引：启动时缓存全部可上映影片（hot/published）的拼音 key，请求内零 DB 转换。
 */
@Slf4j
@Component
public class FilmPinyinIndex {

    /** 与 SearchFilmsTool 一致的过滤条件：热映/正在上映 */
    private static final List<String> STATUSES = List.of("hot", "published");

    public record FilmEntry(Long id, String name, String pinyinKey, String englishKey) {
    }

    @Resource
    private FilmMapper filmMapper;

    @Resource
    private PinyinConverter pinyinConverter;

    @Resource
    private FuzzyMatcher fuzzyMatcher;

    /** 启动后不可变，线程安全 */
    private volatile List<FilmEntry> entries = List.of();

    @PostConstruct
    public void init() {
        try {
            List<Film> films = filmMapper.selectListByQuery(
                    QueryWrapper.create().in(Film::getStatus, STATUSES));
            this.entries = films.stream()
                    .map(f -> new FilmEntry(
                            f.getId(),
                            f.getName(),
                            pinyinConverter.toPinyinKey(f.getName()),
                            f.getEnglishName() == null ? "" : pinyinConverter.toPinyinKey(f.getEnglishName())))
                    .toList();
            log.info("FilmPinyinIndex 加载 {} 部影片", entries.size());
        } catch (Exception e) {
            log.warn("FilmPinyinIndex 初始化失败，拼音纠错不可用: {}", e.getMessage());
            this.entries = List.of();
        }
    }

    /**
     * 拼音匹配：返回 best 命中（含置信度与来源）；无命中返回 empty。
     * 记录 secondScore 用于多候选歧义判定。
     */
    public Optional<FuzzyMatch> matchPinyin(String keyword) {
        String userKey = pinyinConverter.toPinyinKey(keyword);
        if (userKey.isEmpty()) {
            return Optional.empty();
        }
        FilmEntry best = null;
        int bestScore = 0;
        int secondScore = 0;
        for (FilmEntry e : entries) {
            int s = fuzzyMatcher.scorePinyin(userKey, e.pinyinKey);
            if (s >= FuzzyMatcher.MATCH_THRESHOLD) {
                if (s > bestScore) {
                    secondScore = bestScore;
                    bestScore = s;
                    best = e;
                } else if (s > secondScore) {
                    secondScore = s;
                }
            }
        }
        if (best == null) {
            // 英文名兜底：纯 ASCII 输入对 englishKey 做 equals/前缀
            if (userKey.matches("[a-z0-9]+")) {
                for (FilmEntry e : entries) {
                    if (!e.englishKey().isEmpty()
                            && (e.englishKey().equals(userKey) || e.englishKey().startsWith(userKey))) {
                        best = e;
                        bestScore = 88;
                        secondScore = 0;
                        break;
                    }
                }
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        FuzzyMatch.Source source = resolveSource(bestScore);
        int confidence = bestScore;
        // 多候选歧义：best 与 second 接近（≤5）且置信不足 90 → 落确认档，由回复侧向用户确认
        if (bestScore < 90 && bestScore - secondScore <= 5) {
            confidence = Math.min(bestScore, 80);
        }
        return Optional.of(new FuzzyMatch(keyword, best.name(), best.id(), confidence, source));
    }

    private FuzzyMatch.Source resolveSource(int score) {
        if (score >= 100) {
            return FuzzyMatch.Source.PINYIN_EXACT;
        }
        if (score >= 82) {
            return FuzzyMatch.Source.PINYIN_PREFIX;
        }
        return FuzzyMatch.Source.PINYIN_EDIT;
    }

    /** 别名 → 实体回查（标准名精确匹配 DB 名） */
    public Optional<FilmEntry> findByNameExact(String canonicalName) {
        if (canonicalName == null) {
            return Optional.empty();
        }
        String key = pinyinConverter.normalize(canonicalName);
        for (FilmEntry e : entries) {
            if (key.equals(pinyinConverter.normalize(e.name()))) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }
}
