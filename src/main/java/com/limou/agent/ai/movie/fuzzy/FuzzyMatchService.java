package com.limou.agent.ai.movie.fuzzy;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 模糊匹配门面：别名层优先 → 拼音层兜底。
 * <p>
 * 别名捕获的是拼音完全不像的映射（光头强→熊出没、万达→洛阳万达影城(泉舜店)）；
 * 拼音覆盖别名表没覆盖的同音词（支柱下→蜘蛛侠·崭新之日）。
 */
@Slf4j
@Service
public class FuzzyMatchService {

    /** 标准厅型词汇表（拼音兜底比对对象），与知识库 3.2 同义词库对齐 */
    private static final List<String> HALL_TYPES = List.of(
            "IMAX", "巨幕", "杜比", "4DX", "VIP", "普通", "激光", "ScreenX", "情侣厅");

    @Resource
    private KnowledgeAliasLoader aliasLoader;

    @Resource
    private FilmPinyinIndex filmIndex;

    @Resource
    private CinemaPinyinIndex cinemaIndex;

    @Resource
    private PinyinConverter pinyinConverter;

    @Resource
    private FuzzyMatcher fuzzyMatcher;

    /**
     * 影片匹配：别名优先（回查权威名+ID）→ 拼音兜底。
     */
    public Optional<FuzzyMatch> matchFilm(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Optional.empty();
        }
        // 1. 别名
        Optional<String> alias = aliasLoader.lookupFilm(keyword);
        if (alias.isPresent()) {
            Optional<FilmPinyinIndex.FilmEntry> entry = filmIndex.findByNameExact(alias.get());
            if (entry.isPresent()) {
                FilmPinyinIndex.FilmEntry e = entry.get();
                return Optional.of(new FuzzyMatch(keyword, e.name(), e.id(), 95, FuzzyMatch.Source.ALIAS));
            }
            // 别名标准名在 DB 中不存在 → 降级继续拼音（兜底防丢失）
            log.warn("影片别名目标未在 DB 找到: '{}' -> '{}'", keyword, alias.get());
        }
        // 2. 拼音
        return filmIndex.matchPinyin(keyword);
    }

    /**
     * 影院匹配：别名优先 → 拼音兜底。
     */
    public Optional<FuzzyMatch> matchCinema(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Optional.empty();
        }
        Optional<String> alias = aliasLoader.lookupCinema(keyword);
        if (alias.isPresent()) {
            Optional<CinemaPinyinIndex.CinemaEntry> entry = cinemaIndex.findByNameExact(alias.get());
            if (entry.isPresent()) {
                CinemaPinyinIndex.CinemaEntry e = entry.get();
                return Optional.of(new FuzzyMatch(keyword, e.name(), e.id(), 95, FuzzyMatch.Source.ALIAS));
            }
            log.warn("影院别名目标未在 DB 找到: '{}' -> '{}'", keyword, alias.get());
        }
        return cinemaIndex.matchPinyin(keyword);
    }

    /**
     * 影院匹配（限定影院子集，用于按影片排片找影院）。别名不适用（别名可能指向无排片影院）。
     */
    public Optional<FuzzyMatch> matchCinemaWithin(Collection<Long> cinemaIds, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Optional.empty();
        }
        return cinemaIndex.matchPinyinWithin(cinemaIds, keyword);
    }

    /**
     * 厅型归一化：别名 → 标准厅型码；否则对标准厅型词汇表做拼音比对。
     * 返回 null 表示无法归一化（保持原值由原逻辑处理）。
     */
    public String normalizeHallType(String hallType) {
        if (hallType == null || hallType.isBlank()) {
            return null;
        }
        String input = hallType.trim();
        Optional<String> alias = aliasLoader.lookupHallType(input);
        if (alias.isPresent()) {
            String normalized = alias.get();
            if (!normalized.equalsIgnoreCase(input)) {
                return normalized;
            }
        }
        // 拼音比对标准厅型词汇表：取 best，置信 ≥ 阈值才归一化
        String userKey = pinyinConverter.toPinyinKey(input);
        if (userKey.isEmpty()) {
            return null;
        }
        String best = null;
        int bestScore = 0;
        for (String ht : HALL_TYPES) {
            String candKey = pinyinConverter.toPinyinKey(ht);
            int s = fuzzyMatcher.scorePinyin(userKey, candKey);
            if (s > bestScore) {
                bestScore = s;
                best = ht;
            }
        }
        if (best != null && bestScore >= FuzzyMatcher.MATCH_THRESHOLD) {
            return best;
        }
        return null;
    }

    /**
     * 厅型拼音兜底：input 与 dbType/dbName 的拼音 key 相等或互相包含。
     */
    public boolean hallTypePinyinMatch(String input, String dbType, String dbName) {
        String userKey = pinyinConverter.toPinyinKey(input);
        if (userKey.isEmpty()) {
            return false;
        }
        if (pinyinMatch(dbType, userKey) || pinyinMatch(dbName, userKey)) {
            return true;
        }
        // 编辑距离档（如 巨目 vs 巨幕、度比 vs 杜比）
        String candTypeKey = pinyinConverter.toPinyinKey(dbType);
        if (!candTypeKey.isEmpty()) {
            int s = fuzzyMatcher.scorePinyin(userKey, candTypeKey);
            if (s >= FuzzyMatcher.MATCH_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    /**
     * 厅名拼音兜底：input 与 dbName 拼音 key 相等或互相包含。
     */
    public boolean hallNamePinyinMatch(String input, String dbName) {
        String userKey = pinyinConverter.toPinyinKey(input);
        if (userKey.isEmpty()) {
            return false;
        }
        return pinyinMatch(dbName, userKey);
    }

    private boolean pinyinMatch(String candidate, String userKey) {
        String candKey = pinyinConverter.toPinyinKey(candidate);
        return !candKey.isEmpty() && (candKey.equals(userKey) || candKey.contains(userKey) || userKey.contains(candKey));
    }
}
