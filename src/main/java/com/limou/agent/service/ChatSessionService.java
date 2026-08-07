package com.limou.agent.service;

import com.mybatisflex.core.service.IService;
import com.limou.agent.model.entity.ChatSession;

/**
 *  服务层。
 *
 * @author 李振南
 */
public interface ChatSessionService extends IService<ChatSession> {

    /**
     * 获取当前会话（最新一条，无则返回 null，不再自动创建）
     */
    ChatSession getCurrent(Long userId);

    /**
     * 强制创建新会话（不复用，始终新建）
     */
    ChatSession createNew(Long userId);

    /**
     * 查询用户的所有会话（按编辑时间倒序）
     */
    java.util.List<ChatSession> listByUser(Long userId);

    /**
     * 重命名会话
     */
    boolean rename(Long sessionId, String newName);

}
