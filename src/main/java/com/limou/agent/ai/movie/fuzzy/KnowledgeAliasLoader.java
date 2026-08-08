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
 * 影片别名只来自 md 解析（无代码常量兜底，用户要求不硬编码）；影院/厅型别名以代码常量
 * {@link #DEFAULT_CINEMA_ALIASES}/{@link #DEFAULT_HALL_ALIASES} 为基底、md 覆盖补充。
 * 解析失败仅 warn 不阻断启动。
 */
@Slf4j
@Component
public class KnowledgeAliasLoader {

    private static final String DOC_PATH = "classpath*:document/输入纠错与意图映射.md";

    private static final String SECTION_FILM = "影片名称纠错";
    /** 2.1 影片模糊匹配规则段（主演/剧情/IP 角色映射），并入影片别名表 */
    private static final String SECTION_FILM_RULE = "影片模糊匹配规则";
    private static final String SECTION_CINEMA = "影院名称纠错";
    private static final String SECTION_HALL = "影厅类型纠错";

    /**
     * 影片别名无代码常量兜底：用户要求别名只来自知识库 md，不硬编码。
     * md 解析失败/缺失时 filmAliases 为空 → 匹配层降级到拼音（片名/主演/导演/英文名）。
     * 影院/厅型仍保留代码兜底（与 md 1.2/1.3 等价，避免回归）。
     */

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

    /** 影片别名仅来自 md 解析（无代码兜底），md 缺失时为空 Map */
    private final Map<String, String> filmAliases = new HashMap<>();
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
                // 跳过表头（1.1/1.2/1.3 为「用户输入」，2.1 为「用户表述」，统一按「用户」判断）
                if (aliasCell.contains("用户") || canonical.contains("纠错")) {
                    continue;
                }
                if (aliasCell.isEmpty() || canonical.isEmpty()) {
                    continue;
                }
                // 多目标/含评分行防污染：应匹配影片含分隔符或括号（如 2.1 的「动画片/喜剧/评分最高的」）
                // → 这类行是多影片映射或带排序提示，不入别名表（别名表仅支持 单别名→单标准名）
                if (canonical.matches(".*[/()（）、，,].*")) {
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
        if (section.contains(SECTION_FILM) || section.contains(SECTION_FILM_RULE)) {
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
