package com.limou.agent.ai.movie.tools;

import cn.hutool.json.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limou.agent.ai.tools.BaseTool;
import com.limou.agent.mapper.FilmMapper;
import com.limou.agent.model.entity.Film;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Tool(description = "搜索影片，支持按名称关键词和影片类型筛选。返回影片列表JSON，包含影片ID、名称、类型、评分、时长、海报、简介")
    public String searchFilms(
            @ToolParam(description = "影片名称关键词（可选）") String keyword,
            @ToolParam(description = "影片类型，如 喜剧/动作/科幻/悬疑（可选）") String type,
            @ToolParam(description = "排序方式: rating_desc(按评分降序，默认) / rating_asc") String sort
    ) {
        try {
            QueryWrapper wrapper = QueryWrapper.create()
                    .eq(Film::getStatus, "published");

            // 关键词搜索：按名称模糊匹配
            if (keyword != null && !keyword.isBlank()) {
                wrapper.like(Film::getName, keyword);
            }
            // 类型筛选
            if (type != null && !type.isBlank()) {
                wrapper.like(Film::getType, type);
            }

            // 按评分排序
            if ("rating_asc".equals(sort)) {
                wrapper.orderBy(Film::getRating, true);
            } else {
                wrapper.orderBy(Film::getRating, false);
            }

            wrapper.limit(10);

            List<Film> films = filmMapper.selectListByQuery(wrapper);

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
