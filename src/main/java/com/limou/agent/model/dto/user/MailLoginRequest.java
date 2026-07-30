package com.limou.agent.model.dto.user;

import lombok.Data;
import java.io.Serializable;

@Data
public class MailLoginRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 邮箱地址 */
    private String email;

    /** 邮箱收到的验证码 */
    private String code;
}
