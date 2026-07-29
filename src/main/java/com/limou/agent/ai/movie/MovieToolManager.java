package com.limou.agent.ai.movie;

import com.limou.agent.ai.movie.tools.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

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

    /**
     * 电影票 Agent 专用工具回调数组
     * 使用 @Qualifier("movieToolCallbacks") 注入到 MovieAgentFactory
     */
    @Bean
    public ToolCallback[] movieToolCallbacks() {
        ToolCallback[] callbacks = org.springframework.ai.support.ToolCallbacks.from(
                searchFilmsTool,
                searchCinemasTool,
                searchSchedulesTool,
                getSeatMapTool,
                lockSeatsTool,
                createOrderTool,
                payOrderTool,
                getUserPreferenceTool
        );
        log.info("电影票 Agent 工具注册完成，共 {} 个", callbacks.length);
        return callbacks;
    }
}
