package com.limou.agent.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.limou.agent.exception.BusinessException;
import com.limou.agent.exception.ErrorCode;
import com.limou.agent.model.dto.chathistory.ChatHistoryQueryRequest;
import com.limou.agent.model.enums.ChatHistoryMessageTypeEnum;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.limou.agent.model.entity.ChatHistory;
import com.limou.agent.mapper.ChatHistoryMapper;
import com.limou.agent.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 对话历史 服务层实现。
 *
 * @author 李振南
 */
@Slf4j
@Service
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory> implements ChatHistoryService {

    @Override
    public QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest) {
        if (chatHistoryQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = chatHistoryQueryRequest.getId();
        Long sessionId = chatHistoryQueryRequest.getSessionId();
        String messageType = chatHistoryQueryRequest.getMessageType();
        Long userId = chatHistoryQueryRequest.getUserId();
        String sortField = chatHistoryQueryRequest.getSortField();
        String sortOrder = chatHistoryQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .eq("sessionId", sessionId)
                .eq("messageType", messageType)
                .eq("userId", userId)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    @Override
    public int loadChatHistory(Long sessionId, ChatMemory chatMemory, int maxCount) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("sessionId", sessionId)
                .orderBy(ChatHistory::getCreateTime, false)
                .limit(1, maxCount);
        List<ChatHistory> historyList = list(queryWrapper);
        if (CollUtil.isEmpty(historyList)) {
            return 0;
        }
        historyList = historyList.reversed();
        int loadedCount = 0;
        chatMemory.clear(sessionId.toString());
        for (ChatHistory history : historyList) {
            if (history.getMessageType().equals(ChatHistoryMessageTypeEnum.USER.getValue())) {
                chatMemory.add(sessionId.toString(), new UserMessage(history.getMessage()));
                loadedCount++;
            } else if (history.getMessageType().equals(ChatHistoryMessageTypeEnum.AI.getValue())) {
                chatMemory.add(sessionId.toString(), new AssistantMessage(history.getMessage()));
                loadedCount++;
            }
        }
        log.info("加载历史记录：sessionId:{},数量：{}", sessionId, loadedCount);
        return loadedCount;
    }

}
