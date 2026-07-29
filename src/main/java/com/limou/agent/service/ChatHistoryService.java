package com.limou.agent.service;

import com.limou.agent.model.dto.chathistory.ChatHistoryQueryRequest;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.limou.agent.model.entity.ChatHistory;
import org.springframework.ai.chat.memory.ChatMemory;

/**
 * 对话历史 服务层。
 *
 * @author 李振南
 */
public interface ChatHistoryService extends IService<ChatHistory> {

    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);

    /**
     * 从数据库加载会话历史到对话记忆
     *
     * @param sessionId 会话ID
     * @param chatMemory 对话记忆
     * @param maxCount  最大加载数量
     * @return 实际加载数量
     */
    int loadChatHistory(Long sessionId, ChatMemory chatMemory, int maxCount);

}
