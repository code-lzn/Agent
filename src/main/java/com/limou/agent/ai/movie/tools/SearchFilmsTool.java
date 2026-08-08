package com.limou.agent.ai.movie.tools;

import cn.hutool.json.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limou.agent.ai.movie.ConversationContext;
import com.limou.agent.ai.movie.MovieStateManager;
import com.limou.agent.ai.movie.fuzzy.FuzzyMatch;
import com.limou.agent.ai.movie.fuzzy.FuzzyMatchService;
import com.limou.agent.mapper.FilmMapper;
import com.limou.agent.model.dto.movie.ConversationState;
import com.limou.agent.model.entity.Film;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * 影片搜索工具
 * 支持按名称关键词、影片类型搜索，按评分排序
 */
@Slf4j
@Component
public class SearchFilmsTool extends BaseTool {

    @Resource
    private FilmMapper filmMapper;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private MovieStateManager stateManager;

    @Resource
    private FuzzyMatchService fuzzyMatchService;

    @Tool(description = "搜索影片，支持按名称关键词和影片类型筛选。返回影片列表JSON，包含影片ID、名称、类型、评分、时长、海报、简介。最多返回5部，推荐时向用户展示3-5部即可，不要全部列出")
    public String searchFilms(
            @ToolParam(description = "影片名称关键词（可选）") String keyword,
            @ToolParam(description = "影片类型，如 喜剧/动作/科幻/悬疑（可选）") String type,
            @ToolParam(description = "排序方式: rating_desc(按评分降序，默认) / rating_asc") String sort
    ) {
        try {
            List<Film> films;
            boolean isRecommendation = (keyword == null || keyword.isBlank())
                    && (type == null || type.isBlank());

            if (isRecommendation) {
                // ★ 推荐场景（无关键词/类型）：从可上映影片中随机挑 3-5 部，避免每次固定同一批高分片
                List<Film> all = filmMapper.selectListByQuery(
                        QueryWrapper.create()
                                // 可上映影片：热映 hot + 正在上映 published 都可推荐（草稿/下线不展示）
                                .in(Film::getStatus, List.of("hot", "published")));
                Collections.shuffle(all);
                int randomCount = 3 + new Random().nextInt(3); // 3 ~ 5 随机
                films = all.stream()
                        .limit(randomCount)
                        .sorted(Comparator.comparing(Film::getRating,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .collect(Collectors.toList());
            } else {
                // 搜索场景（用户点名/按类型）：按关键词匹配 + 评分排序，最多 5 部
                QueryWrapper wrapper = QueryWrapper.create()
                        .in(Film::getStatus, List.of("hot", "published"));
                if (keyword != null && !keyword.isBlank()) {
                    wrapper.like(Film::getName, keyword);
                }
                if (type != null && !type.isBlank()) {
                    // ★ 用户常说"动作片""喜剧片"，DB 里存的是"动作""喜剧"
                    // 逐步去后缀：片/电影/影片/类 → 得到核心类型词
                    String normalized = type
                            .replaceAll("(电影|影片|片|类)$", "")
                            .trim();
                    log.info("searchFilms type 归一化: '{}' -> '{}'", type, normalized);
                    wrapper.like(Film::getType, normalized.isEmpty() ? type : normalized);
                }
                if ("rating_asc".equals(sort)) {
                    wrapper.orderBy(Film::getRating, true);
                } else {
                    wrapper.orderBy(Film::getRating, false);
                }
                wrapper.limit(5);
                films = filmMapper.selectListByQuery(wrapper);
            }

            // ★ 模糊纠错：SQL like 查空且有关键词时，降级拼音/别名匹配
            // （支柱下 → 蜘蛛侠·崭新之日；"蜘蛛侠" → "蜘蛛侠·崭新之日"）
            FuzzyMatch fuzzyHit = null;
            if (films.isEmpty() && keyword != null && !keyword.isBlank()) {
                java.util.Optional<FuzzyMatch> fm = fuzzyMatchService.matchFilm(keyword);
                if (fm.isPresent() && fm.get().matchedId() != null) {
                    Film matched = filmMapper.selectOneById(fm.get().matchedId());
                    if (matched != null) {
                        films = java.util.List.of(matched);
                        fuzzyHit = fm.get();
                        log.info("SearchFilms 模糊纠错: '{}' -> '{}' (置信度 {}, source {})",
                                keyword, fuzzyHit.matchedName(), fuzzyHit.confidence(), fuzzyHit.source());
                    }
                }
            }

            List<Map<String, Object>> filmList = films.stream().map(f -> {
                Map<String, Object> map = new HashMap<>();
                map.put("filmId", f.getId());
                map.put("name", f.getName());
                map.put("englishName", f.getEnglishName());
                map.put("type", f.getType());
                map.put("rating", f.getRating());
                map.put("duration", f.getDuration());
                map.put("posterUrl", f.getPosterUrl());
                map.put("director", f.getDirector());
                map.put("actors", f.getActors());
                map.put("description", f.getDescription());
                map.put("releaseDate", f.getReleaseDate() != null ? f.getReleaseDate().toString() : null);
                return map;
            }).collect(Collectors.toList());

            Map<String, Object> result = new HashMap<>();
            result.put("films", filmList);
            result.put("total", filmList.size());
            // 模糊纠错信息（前端/LLM 可选展示；不影响既有结构）
            if (fuzzyHit != null) {
                result.put("correctedName", fuzzyHit.matchedName());
                result.put("fuzzy", true);
                result.put("fuzzyConfidence", fuzzyHit.confidence());
                // 匹配依据：片名 / 主演:吴京 / 导演:郭帆 / 别名 / 英文名；LLM 据此判断是"用户说对片名"还是"按演员推断"
                result.put("fuzzyBasis", fuzzyHit.matchBasis());
            }

            // ★ ReAct 模式下写回 filmId 到 ConversationState，确保下一轮 Graph 有上下文
            String convId = ConversationContext.get();
            if (convId != null && !filmList.isEmpty()) {
                try {
                    Map<String, Object> firstFilm = filmList.get(0);
                    ConversationState convState = stateManager.getState(convId);
                    // 仅当 state 里还没有 filmId 或者 keyword 与当前 filmName 匹配时才写回
                    if (convState.getFilmId() == null && keyword != null && !keyword.isBlank()) {
                        // 首选精确匹配，其次模糊匹配
                        for (Map<String, Object> f : filmList) {
                            String name = (String) f.get("name");
                            if (name != null && (name.equalsIgnoreCase(keyword)
                                    || name.contains(keyword)
                                    || keyword.contains(name))) {
                                convState.setFilmId(((Number) f.get("filmId")).longValue());
                                convState.setFilmName(name);
                                stateManager.saveState(convId, convState);
                                log.info("SearchFilms 写回 filmId={}: convId={}", convState.getFilmId(), convId);
                                break;
                            }
                        }
                        // 如果只有一个结果，也直接写回
                        if (convState.getFilmId() == null && filmList.size() == 1) {
                            Map<String, Object> only = filmList.get(0);
                            convState.setFilmId(((Number) only.get("filmId")).longValue());
                            convState.setFilmName((String) only.get("name"));
                            stateManager.saveState(convId, convState);
                            log.info("SearchFilms 写回(唯一结果) filmId={}: convId={}", convState.getFilmId(), convId);
                        }
                    }
                } catch (Exception e) {
                    log.warn("SearchFilms 写回状态失败: convId={}", convId, e);
                }
            }

            String json = objectMapper.writeValueAsString(result);
            log.info("searchFilms 查询结果: keyword={}, type={}, 找到{}部影片", keyword, type, filmList.size());
            return json;

        } catch (Exception e) {
            log.error("searchFilms 查询失败", e);
            return "{\"films\":[],\"total\":0,\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    @Override
    public String getToolName() {
        return "searchFilms";
    }

    @Override
    public String getDisplayName() {
        return "搜索影片";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String keyword = arguments.getStr("keyword");
        String type = arguments.getStr("type");
        return String.format("[工具调用] 搜索影片 keyword=%s type=%s", keyword, type);
    }
}
