package com.limou.agent.service;

import com.limou.agent.model.dto.user.UserQueryRequest;
import com.limou.agent.model.vo.LoginUserVO;
import com.limou.agent.model.vo.UserVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.limou.agent.model.entity.User;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 用户 服务层。
 *
 * @author 李振南
 */
public interface UserService extends IService<User> {
    /**
     * 注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */

    Long register(String userAccount, String userPassword, String checkPassword);

    //加密

    /**
     * @param userPassword 用户密码
     * @return String
     */
    String encryptPassword(String userPassword);


    /**
     * 获取当前登录用户
     *
     * @return 当前脱敏登录用户
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request
     * @return 脱敏后的用户
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 获取当前登录用户
     *
     * @return
     */

    User getLoginUser(HttpServletRequest  request);

    /**
     * 用户退出登录
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 将User对象转换为UserVO对象
     * @param user 用户实体对象
     * @return 转换后的用户VO对象
     */
    UserVO getUserVO(User user);

    /**
     * 将User对象列表转换为UserVO对象列表
     * @param userList 用户实体对象列表
     * @return 转换后的用户VO对象列表
     */
    List<UserVO> getUserVOList(List<User> userList);

    QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest);
}

