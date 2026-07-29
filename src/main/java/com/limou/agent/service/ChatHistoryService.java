package com.limou.agent.service;

import com.limou.agent.model.dto.chathistory.ChatHistoryQueryRequest;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.limou.agent.model.entity.ChatHistory;

/**
 * 对话历史 服务层。
 *
 * @author 李振南
 */
public interface ChatHistoryService extends IService<ChatHistory> {

    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);

}
