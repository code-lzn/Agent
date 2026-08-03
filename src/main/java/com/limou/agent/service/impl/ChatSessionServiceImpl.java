package com.limou.agent.service.impl;

import com.limou.agent.mapper.ChatSessionMapper;
import com.limou.agent.model.entity.ChatSession;
import com.limou.agent.service.ChatSessionService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 *  服务层实现。
 *
 * @author 李振南
 */
@Slf4j
@Service
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements ChatSessionService {

    @Override
    public ChatSession getOrCreateCurrent(Long userId) {
        // 1. 查用户最新一条会话（按编辑时间倒序）
        QueryWrapper wrapper = QueryWrapper.create()
                .eq(ChatSession::getUserId, userId)
                .orderBy(ChatSession::getEditTime, false)
                .limit(1);
        ChatSession session = getOne(wrapper);

        if (session != null) {
            log.info("复用已有会话: sessionId={}, userId={}", session.getId(), userId);
            return session;
        }

        // 2. 没找到 → 创建一个
        session = ChatSession.builder()
                .sessionName("新对话")
                .userId(userId)
                .editTime(LocalDateTime.now())
                .build();
        save(session);
        log.info("创建新会话: sessionId={}, userId={}", session.getId(), userId);
        return session;
    }

    @Override
    public ChatSession createNew(Long userId) {
        ChatSession session = ChatSession.builder()
                .sessionName("新对话")
                .userId(userId)
                .editTime(LocalDateTime.now())
                .build();
        save(session);
        log.info("强制新建会话: sessionId={}, userId={}", session.getId(), userId);
        return session;
    }

    @Override
    public java.util.List<ChatSession> listByUser(Long userId) {
        QueryWrapper wrapper = QueryWrapper.create()
                .eq(ChatSession::getUserId, userId)
                .orderBy(ChatSession::getEditTime, false);
        return list(wrapper);
    }

    @Override
    public boolean rename(Long sessionId, String newName) {
        ChatSession session = getById(sessionId);
        if (session == null) return false;
        session.setSessionName(newName);
        session.setEditTime(LocalDateTime.now());
        return updateById(session);
    }

}
