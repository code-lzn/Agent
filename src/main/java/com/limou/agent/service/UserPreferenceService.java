package com.limou.agent.service;

import com.mybatisflex.core.service.IService;
import com.limou.agent.model.entity.UserPreference;

/**
 *  服务层。
 *
 * @author 李振南
 */
public interface UserPreferenceService extends IService<UserPreference> {

    /**
     * 根据用户ID获取偏好。
     *
     * @param userId 用户ID
     * @return 用户偏好，不存在返回 null
     */
    UserPreference getByUserId(Long userId);

    /**
     * 保存或更新用户偏好。
     *
     * @param userId      用户ID
     * @param preference  偏好数据
     * @return 是否成功
     */
    boolean saveOrUpdate(Long userId, UserPreference preference);
}
