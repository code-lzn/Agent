package com.limou.agent.ai.movie.graph.nodes;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.limou.agent.ai.graph.GraphNode;
import com.limou.agent.ai.movie.MovieStateManager;
import com.limou.agent.ai.movie.graph.MovieGraphState;
import com.limou.agent.ai.movie.graph.MovieIntent;
import com.limou.agent.ai.movie.tools.SearchNearbyCinemasTool;
import com.limou.agent.model.dto.movie.ConversationState;
import lombok.extern.slf4j.Slf4j;

/**
 * 附近影院搜索节点
 * 通过高德地图地理编码 + POI 周边搜索，查找指定地点附近的影院
 * 与 SearchCinemaNode 的区别：本节点做地理定位搜索，而非数据库名称匹配
 */
@Slf4j
public class SearchNearbyNode implements GraphNode<MovieGraphState> {

    private final SearchNearbyCinemasTool tool;
    private final MovieStateManager stateManager;

    public SearchNearbyNode(SearchNearbyCinemasTool tool, MovieStateManager stateManager) {
        this.tool = tool;
        this.stateManager = stateManager;
    }

    @Override
    public MovieGraphState execute(MovieGraphState state) {
        if (state.isBlocked()) {
            return state;
        }

        ConversationState convState = state.getConvState();

        // ★ cinemaName 在 search_nearby 意图下存储的是地理位置描述（由 IntentClassifyNode 映射）
        // 优先使用用户说的具体位置，否则用当前城市名
        String location;
        if (convState.getCinemaName() != null && !convState.getCinemaName().isBlank()) {
            location = convState.getCinemaName();
        } else if (convState.getCurrentCity() != null && !convState.getCurrentCity().isBlank()) {
            location = convState.getCurrentCity();
        } else {
            log.warn("SearchNearby: 无位置信息，无法搜索附近影院");
            state.setToolResult("{\"cinemas\":[],\"total\":0,\"error\":\"请提供位置信息，比如城市名或具体地点\"}");
            state.setToolName(MovieIntent.SEARCH_NEARBY.getCode());
            return state;
        }

        String result = tool.searchNearbyCinemas(
                location,              // 位置描述 → 高德地理编码
                5000,                  // 默认半径 5km
                convState.getFilmId(), // 可选：只找有该片排片的影院
                convState.getUserLat(),
                convState.getUserLng());

        state.setToolResult(result);
        state.setToolName(MovieIntent.SEARCH_NEARBY.getCode());
        persistResolvedCinema(result, convState, state.getConversationId());
        log.info("SearchNearby 完成: location={}, conversationId={}", location, state.getConversationId());
        return state;
    }

    /**
     * 将匹配到的影院写回会话状态
     */
    private void persistResolvedCinema(String result, ConversationState convState, String conversationId) {
        try {
            JSONArray cinemas = JSONUtil.parseObj(result).getJSONArray("cinemas");
            if (cinemas == null || cinemas.isEmpty()) return;

            // 优先匹配同名，否则只有一个结果时自动选定
            JSONObject selected = null;
            for (int i = 0; i < cinemas.size(); i++) {
                JSONObject cinema = cinemas.getJSONObject(i);
                if (convState.getCinemaName() != null
                        && convState.getCinemaName().equalsIgnoreCase(cinema.getStr("cinemaName"))) {
                    selected = cinema;
                    break;
                }
            }
            if (selected == null && cinemas.size() == 1) selected = cinemas.getJSONObject(0);
            if (selected == null || selected.getLong("cinemaId") == null) return;

            convState.setCinemaId(selected.getLong("cinemaId"));
            convState.setCinemaName(selected.getStr("cinemaName", convState.getCinemaName()));
            stateManager.saveState(conversationId, convState);
            log.info("SearchNearby 写回 cinemaId={}: conversationId={}", convState.getCinemaId(), conversationId);
        } catch (Exception e) {
            log.warn("SearchNearby 结果解析失败，跳过影院写回: conversationId={}", conversationId, e);
        }
    }
}
