package com.limou.agent.weixin;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信公众号配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "weixin.config")
public class WeixinProperties {

    /** 公众号原始ID */
    private String originalid;

    /** 公众号 Token（用于签名验证） */
    private String token;

    /** 公众号 AppID */
    private String appId;

    /** 公众号 AppSecret */
    private String appSecret;

    /** 模板消息ID */
    private String templateId;

}
