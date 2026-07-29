package com.limou.agent.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.limou.agent.model.entity.ChatSession;
import com.limou.agent.mapper.ChatSessionMapper;
import com.limou.agent.service.ChatSessionService;
import org.springframework.stereotype.Service;

/**
 *  服务层实现。
 *
 * @author 李振南
 */
@Service
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession>  implements ChatSessionService{

}
