package com.limou.agent.ai.movie;

import com.limou.agent.ai.movie.tools.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 电影票 Agent 工具管理器
 * 注册所有电影票相关的工具，生成独立的 ToolCallbackProvider
 * 通过 @Qualifier("movieToolCallbacks") 与代码生成工具隔离
 */
@Slf4j
@Component
public class MovieToolManager {

    @Resource
    private SearchFilmsTool searchFilmsTool;

    @Resource
    private SearchCinemasTool searchCinemasTool;

    @Resource
    private SearchSchedulesTool searchSchedulesTool;

    @Resource
    private GetSeatMapTool getSeatMapTool;

    @Resource
    private LockSeatsTool lockSeatsTool;

    @Resource
    private CreateOrderTool createOrderTool;

    @Resource
    private PayOrderTool payOrderTool;

    @Resource
    private GetUserPreferenceTool getUserPreferenceTool;

    @Resource
    @Qualifier("mcpToolCallbacks")
    private ToolCallbackProvider mcpToolCallbackProvider;
    /**
     * 电影票 Agent 专用工具回调数组
     * 使用 @Qualifier("movieToolCallbacks") 注入到 MovieAgentFactory
     */
    @Bean
    public ToolCallback[] movieToolCallbacks() {
        ToolCallback[] local = org.springframework.ai.support.ToolCallbacks.from(
                searchFilmsTool,
                searchCinemasTool,
                searchSchedulesTool,
                getSeatMapTool,
                lockSeatsTool,
                createOrderTool,
                payOrderTool,
                getUserPreferenceTool
        );

        ToolCallback[] mcp = mcpToolCallbackProvider != null
                ? mcpToolCallbackProvider.getToolCallbacks()
                : new ToolCallback[0];

        ToolCallback[] callbacks = new ToolCallback[local.length + mcp.length];
        System.arraycopy(local, 0, callbacks, 0, local.length);
        System.arraycopy(mcp, 0, callbacks, local.length, mcp.length);

        log.info("电影票 Agent 工具注册完成: 本地 {} 个, MCP {} 个, 共 {} 个",
                local.length, mcp.length, callbacks.length);
        return callbacks;
    }

    /**
     * 获取工具英文名 → 中文显示名的映射（用于流式输出工具状态）
     */
    public Map<String, String> getToolDisplayNames() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(searchFilmsTool.getToolName(), searchFilmsTool.getDisplayName());
        map.put(searchCinemasTool.getToolName(), searchCinemasTool.getDisplayName());
        map.put(searchSchedulesTool.getToolName(), searchSchedulesTool.getDisplayName());
        map.put(getSeatMapTool.getToolName(), getSeatMapTool.getDisplayName());
        map.put(lockSeatsTool.getToolName(), lockSeatsTool.getDisplayName());
        map.put(createOrderTool.getToolName(), createOrderTool.getDisplayName());
        map.put(payOrderTool.getToolName(), payOrderTool.getDisplayName());
        map.put(getUserPreferenceTool.getToolName(), getUserPreferenceTool.getDisplayName());
        return map;
    }
}
