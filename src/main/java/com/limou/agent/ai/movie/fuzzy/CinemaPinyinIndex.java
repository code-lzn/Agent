package com.limou.agent.ai.movie.fuzzy;

import com.limou.agent.mapper.CinemaMapper;
import com.limou.agent.model.entity.Cinema;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 影院拼音索引：启动时缓存全部已发布影院（status=published）的拼音 key。
 */
@Slf4j
@Component
public class CinemaPinyinIndex {

    public record CinemaEntry(Long id, String name, String pinyinKey) {
    }

    @Resource
    private CinemaMapper cinemaMapper;

    @Resource
    private PinyinConverter pinyinConverter;

    @Resource
    private FuzzyMatcher fuzzyMatcher;

    private volatile List<CinemaEntry> entries = List.of();

    @PostConstruct
    public void init() {
        try {
            List<Cinema> cinemas = cinemaMapper.selectListByQuery(
                    QueryWrapper.create().eq(Cinema::getStatus, "published"));
            this.entries = cinemas.stream()
                    .map(c -> new CinemaEntry(c.getId(), c.getName(), pinyinConverter.toPinyinKey(c.getName())))
                    .toList();
            log.info("CinemaPinyinIndex 加载 {} 家影院", entries.size());
        } catch (Exception e) {
            log.warn("CinemaPinyinIndex 初始化失败，拼音纠错不可用: {}", e.getMessage());
            this.entries = List.of();
        }
    }

    /**
     * 拼音匹配（全部已发布影院）。与 FilmPinyinIndex 同评分逻辑。
     */
    public Optional<FuzzyMatch> matchPinyin(String keyword) {
        return matchPinyin(entries, keyword);
    }

    /**
     * 拼音匹配（限定影院子集，用于按影片排片找影院时避免命中无排片影院）。
     */
    public Optional<FuzzyMatch> matchPinyinWithin(Collection<Long> cinemaIds, String keyword) {
        if (cinemaIds == null || cinemaIds.isEmpty()) {
            return Optional.empty();
        }
        List<CinemaEntry> subset = entries.stream()
                .filter(e -> cinemaIds.contains(e.id()))
                .toList();
        return matchPinyin(subset, keyword);
    }

    private Optional<FuzzyMatch> matchPinyin(List<CinemaEntry> pool, String keyword) {
        String userKey = pinyinConverter.toPinyinKey(keyword);
        if (userKey.isEmpty() || pool.isEmpty()) {
            return Optional.empty();
        }
        CinemaEntry best = null;
        int bestScore = 0;
        int secondScore = 0;
        for (CinemaEntry e : pool) {
            int s = fuzzyMatcher.scorePinyin(userKey, e.pinyinKey());
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
            return Optional.empty();
        }
        int confidence = bestScore;
        if (bestScore < 90 && bestScore - secondScore <= 5) {
            confidence = Math.min(bestScore, 80);
        }
        return Optional.of(new FuzzyMatch(keyword, best.name(), best.id(), confidence, FuzzyMatch.Source.PINYIN_PREFIX));
    }

    /** 别名 → 实体回查 */
    public Optional<CinemaEntry> findByNameExact(String canonicalName) {
        if (canonicalName == null) {
            return Optional.empty();
        }
        String key = pinyinConverter.normalize(canonicalName);
        for (CinemaEntry e : entries) {
            if (key.equals(pinyinConverter.normalize(e.name()))) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }
}
