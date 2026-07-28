package com.limou.agent.ai.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ImageSearchTool extends BaseTool {

    @Value("${pexels.api-key}")
    private String apiKey;

    private static final String PEXELS_SEARCH_URL = "https://api.pexels.com/v1/search";
    private static final int DEFAULT_PER_PAGE = 5;

    @Tool(description = "搜索图片，根据关键词在 Pexels 图片库中搜索相关图片，返回图片 URL 和摄影师信息")
    public String searchImages(
            @ToolParam(description = "搜索关键词，例如：sunset、cat、city skyline")
            String query,
            @ToolParam(description = "返回图片数量，默认5张，最大20张")
            Integer perPage
    ) {
        if (StrUtil.isBlank(query)) {
            return "错误：搜索关键词不能为空";
        }
        int count = (perPage == null || perPage <= 0) ? DEFAULT_PER_PAGE : Math.min(perPage, 20);
        try {
            String responseBody = HttpRequest.get(PEXELS_SEARCH_URL)
                    .header("Authorization", apiKey)
                    .form("query", query)
                    .form("per_page", String.valueOf(count))
                    .form("locale", "zh-CN")
                    .execute()
                    .body();
            JSONObject result = JSONUtil.parseObj(responseBody);
            JSONArray photos = result.getJSONArray("photos");
            if (photos == null || photos.isEmpty()) {
                return "未找到与 \"" + query + "\" 相关的图片";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("搜索结果：共找到 ").append(result.getInt("total_results")).append(" 张图片，展示前 ").append(photos.size()).append(" 张：\n\n");
            for (int i = 0; i < photos.size(); i++) {
                JSONObject photo = photos.getJSONObject(i);
                sb.append(i + 1).append(".\n");
                sb.append("   描述: ").append(photo.getStr("alt")).append("\n");
                sb.append("   摄影师: ").append(photo.getStr("photographer")).append("\n");
                sb.append("   原图URL: ").append(photo.getJSONObject("src").getStr("original")).append("\n");
                sb.append("   预览URL: ").append(photo.getJSONObject("src").getStr("medium")).append("\n");
                sb.append("   Pexels页面: ").append(photo.getStr("url")).append("\n\n");
            }
            log.info("图片搜索完成: query={}, 返回 {} 张", query, photos.size());
            return sb.toString();
        } catch (Exception e) {
            log.error("图片搜索失败: query={}", query, e);
            return "图片搜索请求失败: " + e.getMessage();
        }
    }

    @Override
    public String getToolName() {
        return "searchImages";
    }

    @Override
    public String getDisplayName() {
        return "图片搜索工具";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String query = arguments.getStr("query");
        return String.format("[图片搜索] \"%s\"", query);
    }
}
