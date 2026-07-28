package com.limou.agent.ai.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.limou.agent.model.entity.ImageResource;
import com.limou.agent.model.enums.ImageCategoryEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class UndrawIllustrationTool extends BaseTool {
    private static final String UNDRAW_API_URL = "https://undraw.co/_next/data/N6M_hYvpIPjDtR8MHPCqU/search/%s.json?term=%s";

    @Tool(description = "搜索插画图片，根据关键词在 Undraw 插画库中搜索相关插画，用于网站的美化和修饰")
    public List<ImageResource> searchIllustrations(
            @ToolParam(description = "搜索关键词，例如：coding、teamwork") String query) {
        int searchCount = 12;
        List<ImageResource> imageList = new ArrayList<>();
        String apiUrl = String.format(UNDRAW_API_URL, query, query);
        try {
            String responseBody = HttpRequest.get(apiUrl).timeout(10000).execute().body();
            JSONObject result = JSONUtil.parseObj(responseBody);
            JSONObject pageProps = result.getJSONObject("pageProps");
            if (pageProps == null) {
                return imageList;
            }
            JSONArray illustrations = pageProps.getJSONArray("initialResults");
            if (illustrations == null || illustrations.isEmpty()) {
                return imageList;
            }
            int actualCount = Math.min(searchCount, illustrations.size());
            for (int i = 0; i < actualCount; i++) {
                JSONObject illustration = illustrations.getJSONObject(i);
                String title = illustration.getStr("title", "插画");
                String media = illustration.getStr("media", "");
                if (StrUtil.isBlank(media)) {
                    continue;
                }
                imageList.add(ImageResource.builder()
                        .description(title)
                        .url(media)
                        .category(ImageCategoryEnum.ILLUSTRATION)
                        .build());
            }
        } catch (Exception e) {
            log.error("Undraw API 调用失败: {}", e.getMessage(), e);
        }
        return imageList;
    }

    @Override
    public String getToolName() {
        return "searchIllustrations";
    }

    @Override
    public String getDisplayName() {
        return "插画搜索工具";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String query = arguments.getStr("query");
        return String.format("[搜索插画] %s", query);
    }
}
