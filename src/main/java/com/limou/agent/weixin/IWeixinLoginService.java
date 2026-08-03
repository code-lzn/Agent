package com.limou.agent.weixin;

import java.io.IOException;

/**
 * 微信登录服务接口
 */
public interface IWeixinLoginService {

    /**
     * 创建带参数二维码的 ticket
     */
    String createQrCodeTicket() throws Exception;

    /**
     * 检查登录状态（根据 ticket 获取 openid）
     */
    String checkLogin(String ticket);

    /**
     * 保存登录状态（ticket → openid 映射）
     */
    void saveLoginState(String ticket, String openid) throws IOException;

}
