package com.limou.agent.weixin.model;

import lombok.Data;

/**
 * 获取 Access token 响应对象
 */
@Data
public class WeixinTokenRes {

    private String access_token;
    private int expires_in;
    private String errcode;
    private String errmsg;

}
