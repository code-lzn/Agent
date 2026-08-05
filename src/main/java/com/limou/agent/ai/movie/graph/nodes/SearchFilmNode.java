package com.limou.agent.ai.movie.graph.nodes;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.limou.agent.ai.graph.GraphNode;
import com.limou.agent.ai.movie.MovieStateManager;
import com.limou.agent.ai.movie.graph.MovieGraphState;
import com.limou.agent.ai.movie.graph.MovieIntent;
import com.limou.agent.ai.movie.tools.SearchFilmsTool;
import com.limou.agent.model.dto.movie.ConversationState;
import lombok.extern.slf4j.Slf4j;

/**
 * 搜索影片节点
 */
@Slf4j
public class SearchFilmNode implements GraphNode<MovieGraphState> {

    private final SearchFilmsTool tool;
    private final MovieStateManager stateManager;

    public SearchFilmNode(SearchFilmsTool tool, MovieStateManager stateManager) {
        this.tool = tool;
        this.stateManager = stateManager;
    }

    @Override
    public MovieGraphState execute(MovieGraphState state) {
        if (state.isBlocked()) {
            return state;
        }

        ConversationState convState = state.getConvState();

        String result = tool.searchFilms(
                convState.getFilmName(),
                convState.getFilmType(),
                "rating_desc");

        state.setToolResult(result);
        state.setToolName(MovieIntent.SEARCH_MOVIE.getCode());
        persistResolvedFilm(result, convState, state.getConversationId());
        log.info("SearchFilm 完成: conversationId={}", state.getConversationId());
        return state;
    }

    private void persistResolvedFilm(String result, ConversationState convState, String conversationId) {
        try {
            JSONArray films = JSONUtil.parseObj(result).getJSONArray("films");
            if (films == null || films.isEmpty()) return;

            String requestedName = convState.getFilmName();
            JSONObject selected = null;

            if (requestedName != null && !requestedName.isBlank()) {
                // ★ 模糊匹配：用户说的简称可能不完全等于 DB 里的全名（如"志愿3" vs "志愿军：雄兵出击3"）
                String normalized = requestedName.trim();
                for (int i = 0; i < films.size(); i++) {
                    JSONObject film = films.getJSONObject(i);
                    String dbName = film.getStr("name");
                    if (dbName == null) continue;
                    // 精确匹配 或 包含匹配（用户简称是 DB 全名的子串）
                    if (dbName.equalsIgnoreCase(normalized)
                            || dbName.contains(normalized)
                            || normalized.contains(dbName)) {
                        selected = film;
                        break;
                    }
                }
                // 降级：检查用户输入中的每个词是否都在 DB 名称中出现
                if (selected == null) {
                    String[] words = normalized.split("[\\s：:、，,]+");
                    for (int i = 0; i < films.size(); i++) {
                        JSONObject film = films.getJSONObject(i);
                        String dbName = film.getStr("name");
                        if (dbName == null) continue;
                        boolean allMatch = true;
                        for (String word : words) {
                            if (!word.isBlank() && !dbName.contains(word)) {
                                allMatch = false;
                                break;
                            }
                        }
                        if (allMatch) {
                            selected = film;
                            break;
                        }
                    }
                }
            }
            if (selected == null && films.size() == 1) selected = films.getJSONObject(0);
            if (selected == null || selected.getLong("filmId") == null) return;

            convState.setFilmId(selected.getLong("filmId"));
            convState.setFilmName(selected.getStr("name", convState.getFilmName()));
            stateManager.saveState(conversationId, convState);
            log.info("SearchFilm 写回 filmId={} name={}: conversationId={}",
                    convState.getFilmId(), convState.getFilmName(), conversationId);
        } catch (Exception e) {
            log.warn("SearchFilm 结果解析失败，跳过影片写回: conversationId={}", conversationId, e);
        }
    }
}
