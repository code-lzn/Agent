package com.limou.agent.model.dto.user;
import lombok.Data;
import java.io.Serializable;

@Data
public class SendMailCodeRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 邮箱地址 */
    private String email;

    /** 图形验证码（简单防刷） */
    private String captcha;
}
