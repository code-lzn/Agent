package com.limou.agent.ai.movie.fuzzy;

import com.limou.agent.mapper.FilmMapper;
import com.limou.agent.model.entity.Film;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 影片拼音索引：启动时缓存全部可上映影片（hot/published）的拼音 key，请求内零 DB 转换。
 * <p>
 * 除片名/英文名外，额外索引导演与主演拼音——让所有影片都能按主演/导演自动匹配
 * （如"吴京"→流浪地球3、"郭帆"→流浪地球3），无需手工加别名。
 */
@Slf4j
@Component
public class FilmPinyinIndex {

    /** 与 SearchFilmsTool 一致的过滤条件：热映/正在上映 */
    private static final List<String> STATUSES = List.of("hot", "published");

    /** 片名命中相对主演/导演命中的优先级加成：片名优先，避免演员拼音误抢精确匹配 */
    private static final int TITLE_BOOST = 8;
    /** 该主演/导演在全库仅对应 1 部影片时的置信度上限（>90 静默档） */
    private static final int CAST_SINGLE_CAP = 92;
    /** 该主演/导演对应多部影片时的置信度上限（落 50-90 确认档，由回复侧确认） */
    private static final int CAST_MULTI_CAP = 82;

    /** 人名（导演/主演）拼音 key */
    public record PersonKey(String rawName, String key) {
    }

    public record FilmEntry(Long id, String name, String pinyinKey, String englishKey,
                            List<PersonKey> directors, List<PersonKey> actors) {
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
                            f.getEnglishName() == null ? "" : pinyinConverter.toPinyinKey(f.getEnglishName()),
                            splitPersonKeys(f.getDirector()),
                            splitPersonKeys(f.getActors())))
                    .toList();
            log.info("FilmPinyinIndex 加载 {} 部影片", entries.size());
        } catch (Exception e) {
            log.warn("FilmPinyinIndex 初始化失败，拼音纠错不可用: {}", e.getMessage());
            this.entries = List.of();
        }
    }

    /** 缓存刷新入口（影片新增/编辑/状态变更后调用），内部重新加载全部索引 */
    public synchronized void refresh() {
        init();
    }

    /**
     * 拆分人名（导演/主演）：支持中文顿号/逗号/斜杠/分号/空格分隔，逐项转拼音 key，去空去重。
     */
    private List<PersonKey> splitPersonKeys(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("[、,，/；;\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> new PersonKey(s, pinyinConverter.toPinyinKey(s)))
                .filter(p -> !p.key().isEmpty())
                .distinct()
                .toList();
    }

    /**
     * 主演/导演候选命中
     */
    private record CastCandidate(FilmEntry entry, PersonKey person, boolean director,
                                 int score, int secondScore, int samePersonFilmCount) {
    }

    /**
     * 拼音匹配：标题（片名）匹配优先 → 主演/导演兜底 → 英文名兜底。
     * 返回 best 命中（含置信度、来源、匹配依据）；无命中返回 empty。
     */
    public Optional<FuzzyMatch> matchPinyin(String keyword) {
        String userKey = pinyinConverter.toPinyinKey(keyword);
        if (userKey.isEmpty()) {
            return Optional.empty();
        }
        TitleCandidate title = bestTitleMatch(userKey);
        CastCandidate cast = bestCastMatch(userKey);

        FilmEntry best;
        int bestScore;
        int secondScore;
        FuzzyMatch.Source source;
        String basis;
        if (title == null) {
            if (cast == null) {
                // 英文名兜底：纯 ASCII 输入对 englishKey 做 equals/前缀
                if (userKey.matches("[a-z0-9]+")) {
                    for (FilmEntry e : entries) {
                        if (!e.englishKey().isEmpty()
                                && (e.englishKey().equals(userKey) || e.englishKey().startsWith(userKey))) {
                            best = e;
                            bestScore = 88;
                            secondScore = 0;
                            source = FuzzyMatch.Source.PINYIN_PREFIX;
                            basis = "英文名";
                            int confidence = capConfidence(bestScore, secondScore);
                            return Optional.of(new FuzzyMatch(keyword, best.name(), best.id(),
                                    confidence, source, basis));
                        }
                    }
                }
                return Optional.empty();
            }
            // 主演/导演兜底（片名未命中）
            best = cast.entry();
            bestScore = capCastScore(cast);
            secondScore = Math.max(title == null ? 0 : title.secondScore(), cast.secondScore());
            source = cast.director() ? FuzzyMatch.Source.DIRECTOR : FuzzyMatch.Source.ACTOR;
            basis = (cast.director() ? "导演:" : "主演:") + cast.person().rawName();
        } else if (cast != null && cast.score() >= title.score() + TITLE_BOOST) {
            // 主演/导演明显更优（片名也有弱命中时，用户输入更像人名）
            best = cast.entry();
            bestScore = capCastScore(cast);
            secondScore = Math.max(title.secondScore(), cast.secondScore());
            source = cast.director() ? FuzzyMatch.Source.DIRECTOR : FuzzyMatch.Source.ACTOR;
            basis = (cast.director() ? "导演:" : "主演:") + cast.person().rawName();
        } else {
            // 片名命中优先
            best = title.entry();
            bestScore = title.score();
            secondScore = title.secondScore();
            source = resolveSource(bestScore);
            basis = "片名";
        }

        int confidence = capConfidence(bestScore, secondScore);
        return Optional.of(new FuzzyMatch(keyword, best.name(), best.id(), confidence, source, basis));
    }

    /** 主演/导演置信度封顶：一人多片 → 确认档；一人一片 → 92（静默档下沿） */
    private int capCastScore(CastCandidate cast) {
        return Math.min(cast.score(),
                cast.samePersonFilmCount() <= 1 ? CAST_SINGLE_CAP : CAST_MULTI_CAP);
    }

    /** 多候选歧义确认带：best 与 second 接近（≤5）且置信不足 90 → 落确认档 */
    private int capConfidence(int bestScore, int secondScore) {
        int confidence = bestScore;
        if (bestScore < 90 && bestScore - secondScore <= 5) {
            confidence = Math.min(bestScore, 80);
        }
        return confidence;
    }

    /** 片名通道：对每个 entry 的 pinyinKey 评分，取 best + second */
    private TitleCandidate bestTitleMatch(String userKey) {
        FilmEntry best = null;
        int bestScore = 0;
        int secondScore = 0;
        for (FilmEntry e : entries) {
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
            return null;
        }
        return new TitleCandidate(best, bestScore, secondScore);
    }

    private record TitleCandidate(FilmEntry entry, int score, int secondScore) {
    }

    /**
     * 主演/导演通道：遍历所有 entry 的导演/主演 key，取全局 best + second（按不同 filmId），
     * 并统计该人名对应影片数（驱动一人多片确认档）。
     */
    private CastCandidate bestCastMatch(String userKey) {
        CastCandidate best = null;
        int secondScore = 0;
        Long bestFilmId = null;
        for (FilmEntry e : entries) {
            List<PersonKey> persons = e.directors();
            boolean dir = true;
            for (int pass = 0; pass < 2; pass++) {
                if (pass == 1) {
                    persons = e.actors();
                    dir = false;
                }
                for (PersonKey p : persons) {
                    int s = fuzzyMatcher.scorePinyin(userKey, p.key());
                    if (s < FuzzyMatcher.MATCH_THRESHOLD) {
                        continue;
                    }
                    int samePersonFilmCount = countFilmsOfPerson(p.key());
                    if (best == null || s > best.score()) {
                        secondScore = Math.max(secondScore, best == null ? 0 : best.score());
                        best = new CastCandidate(e, p, dir, s, secondScore, samePersonFilmCount);
                        bestFilmId = e.id();
                    } else if (s > secondScore && !e.id().equals(bestFilmId)) {
                        secondScore = s;
                    }
                }
            }
        }
        return best;
    }

    /** 全库中拥有相同人名 key 的影片数（含当前 entry） */
    private int countFilmsOfPerson(String personKey) {
        int count = 0;
        for (FilmEntry e : entries) {
            for (PersonKey d : e.directors()) {
                if (d.key().equals(personKey)) {
                    count++;
                    break;
                }
            }
            if (count > 0) {
                continue;
            }
            for (PersonKey a : e.actors()) {
                if (a.key().equals(personKey)) {
                    count++;
                    break;
                }
            }
        }
        return count;
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
