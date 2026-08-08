package com.limou.agent.ai.movie.fuzzy;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 知识库别名加载器：解析 {@code document/输入纠错与意图映射.md} 的纠错表为别名 Map。
 * <p>
 * 以代码常量 {@link #DEFAULT_ALIASES} 为基底（解析失败/内容缺失时兜底），
 * md 表格解析成功后覆盖并补充。解析失败仅 warn 不阻断启动。
 */
@Slf4j
@Component
public class KnowledgeAliasLoader {

    private static final String DOC_PATH = "classpath*:document/输入纠错与意图映射.md";

    private static final String SECTION_FILM = "影片名称纠错";
    private static final String SECTION_CINEMA = "影院名称纠错";
    private static final String SECTION_HALL = "影厅类型纠错";

    /**
     * 代码常量兜底：md 缺失或解析失败时的别名基底。必须含核心用例（支柱下→蜘蛛侠）。
     * key=规范化后的别名（normalize 结果），value=标准名（DB 权威名）。
     * 影片/影院/厅型各自独立，保证 md 解析失败时三类兜底都不丢失。
     */
    private static final Map<String, String> DEFAULT_FILM_ALIASES = Map.of(
            "支柱下", "蜘蛛侠·崭新之日",
            "蜘蛛侠", "蜘蛛侠·崭新之日",
            "崭新之日", "蜘蛛侠·崭新之日",
            "流浪地球", "流浪地球3",
            "封神", "封神第二部",
            "哪吒", "哪吒之魔童闹海",
            "热辣", "热辣滚烫",
            "熊出没", "熊出没·逆转时空");

    private static final Map<String, String> DEFAULT_CINEMA_ALIASES = Map.of(
            "万达", "洛阳万达影城(泉舜店)",
            "泉舜", "洛阳万达影城(泉舜店)",
            "耀莱", "洛阳耀莱成龙国际影城",
            "奥斯卡", "洛阳奥斯卡国际影城",
            "cgv", "洛阳CGV影城(宝龙广场店)",
            "中影", "洛阳中影国际影城");

    private static final Map<String, String> DEFAULT_HALL_ALIASES = Map.ofEntries(
            Map.entry("巨目", "巨幕"),
            Map.entry("巨木", "巨幕"),
            Map.entry("巨慕", "巨幕"),
            Map.entry("度比", "杜比"),
            Map.entry("度毕", "杜比"),
            Map.entry("请侣厅", "情侣厅"),
            Map.entry("请吕厅", "情侣厅"),
            Map.entry("请铝厅", "情侣厅"),
            Map.entry("imax", "IMAX"),
            Map.entry("4d", "4DX"),
            Map.entry("四维", "4DX"),
            Map.entry("全景厅", "ScreenX"),
            Map.entry("贵宾", "VIP"),
            Map.entry("vip", "VIP"));

    private final Map<String, String> filmAliases = new HashMap<>(DEFAULT_FILM_ALIASES);
    private final Map<String, String> cinemaAliases = new HashMap<>(DEFAULT_CINEMA_ALIASES);
    private final Map<String, String> hallAliases = new HashMap<>(DEFAULT_HALL_ALIASES);

    @jakarta.annotation.Resource
    private ResourcePatternResolver resourcePatternResolver;

    @jakarta.annotation.Resource
    private PinyinConverter pinyinConverter;

    @PostConstruct
    public void init() {
        try {
            Resource[] resources = resourcePatternResolver.getResources(DOC_PATH);
            if (resources.length == 0) {
                log.warn("知识库文档不存在: {}，仅使用内置别名", DOC_PATH);
                return;
            }
            for (Resource resource : resources) {
                parseMd(resource);
            }
            log.info("KnowledgeAliasLoader 加载完成: 影片别名 {} 条, 影院别名 {} 条, 厅型别名 {} 条",
                    filmAliases.size(), cinemaAliases.size(), hallAliases.size());
        } catch (Exception e) {
            log.warn("知识库别名解析失败，仅使用内置别名: {}", e.getMessage());
        }
    }

    /**
     * 解析 markdown：按 {@code ###} 标题识别段落，{@code | a / b | c | d |} 表格行 →
     * 第一列按 {@code /} 拆分别名，第二列为标准名。
     */
    private void parseMd(Resource resource) {
        String currentSection = null;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String t = line.trim();
                if (t.startsWith("### ")) {
                    currentSection = t.substring(4).trim();
                    continue;
                }
                if (currentSection == null || !t.startsWith("|")) {
                    continue;
                }
                Map<String, String> target = sectionTarget(currentSection);
                if (target == null) {
                    continue;
                }
                String[] cells = t.split("\\|");
                if (cells.length < 3) {
                    continue;
                }
                String aliasCell = cells[1].trim();
                String canonical = cells[2].trim();
                // 跳过表头
                if (aliasCell.contains("用户输入") || canonical.contains("纠错")) {
                    continue;
                }
                if (aliasCell.isEmpty() || canonical.isEmpty()) {
                    continue;
                }
                for (String variant : aliasCell.split("/")) {
                    String key = pinyinConverter.normalize(variant).toLowerCase();
                    if (!key.isEmpty()) {
                        target.put(key, canonical);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析知识库文档异常: {}", e.getMessage());
        }
    }

    private Map<String, String> sectionTarget(String section) {
        if (section == null) {
            return null;
        }
        if (section.contains(SECTION_FILM)) {
            return filmAliases;
        }
        if (section.contains(SECTION_CINEMA)) {
            return cinemaAliases;
        }
        if (section.contains(SECTION_HALL)) {
            return hallAliases;
        }
        return null;
    }

    /** 影片别名查询（raw 输入） */
    public Optional<String> lookupFilm(String raw) {
        return lookup(filmAliases, raw);
    }

    /** 影院别名查询 */
    public Optional<String> lookupCinema(String raw) {
        return lookup(cinemaAliases, raw);
    }

    /** 厅型别名查询 */
    public Optional<String> lookupHallType(String raw) {
        return lookup(hallAliases, raw);
    }

    private Optional<String> lookup(Map<String, String> map, String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        // 别名 key 统一小写存储与查询（中文不受影响，英文大小写不敏感）
        String key = pinyinConverter.normalize(raw).toLowerCase();
        if (key.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(map.get(key));
    }

}
