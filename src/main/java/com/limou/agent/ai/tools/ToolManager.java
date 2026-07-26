package com.limou.agent.ai.tools;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ToolManager {
    //将工具注册到这个类中
    @Resource
    private BaseTool[] tools;
    private static final Map<String, BaseTool> toolMap = new HashMap<>();


    @PostConstruct
    public void init() {
        for (BaseTool tool : tools) {
            toolMap.put(tool.getToolName(), tool);
            log.info("注册工具: {}", tool.getToolName());
        }
        log.info("工具注册完成,一共注册 {} 个", toolMap.size());
    }

    /**
     * 根据工具名称获取工具实例
     * @param toolName 工具名称
     * @return 工具实例
     */
    public BaseTool getTool(String toolName) {
        return toolMap.get(toolName);
    }

    //得到所有工具
    @Bean
    public ToolCallback[] toolCallbacks(){
        //进行数据的
        ExitTool exitTool = new ExitTool();
        FileReadTool fileReadTool = new FileReadTool();
        FileWriteTool fileWriteTool = new FileWriteTool();
        FileDeleteTool fileDeleteTool = new FileDeleteTool();
        FileModifyTool fileModifyTool = new FileModifyTool();
        FileDirReadTool fileDirReadTool = new FileDirReadTool();
        return ToolCallbacks.from(
                exitTool,
                fileReadTool,
                fileWriteTool,
                fileDeleteTool,
                fileModifyTool,
                fileDirReadTool
        );

    }
}
