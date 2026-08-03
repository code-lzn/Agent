package com.limou.agent.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.limou.agent.model.entity.UserPreference;
import com.limou.agent.mapper.UserPreferenceMapper;
import com.limou.agent.service.UserPreferenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 *  服务层实现。
 *
 * @author 李振南
 */
@Slf4j
@Service
public class UserPreferenceServiceImpl extends ServiceImpl<UserPreferenceMapper, UserPreference>  implements UserPreferenceService{

    @Override
    public UserPreference getByUserId(Long userId) {
        QueryWrapper qw = QueryWrapper.create().eq("userId", userId);
        return this.getOne(qw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveOrUpdate(Long userId, UserPreference preference) {
        UserPreference existing = getByUserId(userId);
        if (existing != null) {
            preference.setId(existing.getId());
            preference.setUserId(userId);
            return this.updateById(preference);
        } else {
            preference.setUserId(userId);
            return this.save(preference);
        }
    }
}
