package com.limou.agent.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.limou.agent.model.entity.UserPreference;
import com.limou.agent.mapper.UserPreferenceMapper;
import com.limou.agent.service.UserPreferenceService;
import org.springframework.stereotype.Service;

/**
 *  服务层实现。
 *
 * @author 李振南
 */
@Service
public class UserPreferenceServiceImpl extends ServiceImpl<UserPreferenceMapper, UserPreference>  implements UserPreferenceService{

}
