package com.limou.agent.model.dto.user;

import lombok.Data;
import java.io.Serializable;

@Data
public class ResetPasswordRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 邮箱地址 */
    private String email;

    /** 邮箱验证码 */
    private String code;

    /** 新密码 */
    private String newPassword;

    /** 确认新密码 */
    private String checkPassword;
}
