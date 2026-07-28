package com.limou.agent.ai.tools;

import cn.hutool.json.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class WebScrapingTool extends BaseTool {

    @Tool(description = "抓取网页内容，提取页面中的纯文本信息，去除 HTML 标签和脚本")
    public String scrapeWebPage(
            @ToolParam(description = "要抓取的网页 URL") String url) {
        try {
            Document document = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .timeout(10000)
                    .get();
            String text = document.body().text();
            log.info("网页抓取成功: url={}, 文本长度={}", url, text.length());
            return text.length() > 8000 ? text.substring(0, 8000) + "..." : text;
        } catch (Exception e) {
            log.error("网页抓取失败: url={}", url, e);
            return "网页抓取失败: " + e.getMessage();
        }
    }

    @Override
    public String getToolName() {
        return "scrapeWebPage";
    }

    @Override
    public String getDisplayName() {
        return "网页抓取工具";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String url = arguments.getStr("url");
        return String.format("[网页抓取] %s", url);
    }
}
