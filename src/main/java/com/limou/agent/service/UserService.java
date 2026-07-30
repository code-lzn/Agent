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
    /**
     * 发送邮箱验证码
     */
    void sendMailCode(String email);

    /**
     * 邮箱 + 验证码 登录 / 注册（合一）
     */
    LoginUserVO mailLogin(String email, String code, HttpServletRequest request);

    /**
     * 通过邮箱验证码重置密码
     */
    void resetPassword(String email, String code, String newPassword, String checkPassword);

    /** 新用户设置密码（当前密码为默认值时使用，无需旧密码） */
    void setPassword(Long userId, String newPassword, String checkPassword);

    /** 老用户修改密码（需校验旧密码） */
    void changePassword(Long userId, String oldPassword, String newPassword, String checkPassword);
}

