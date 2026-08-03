package com.limou.agent.weixin;

import com.github.benmanes.caffeine.cache.Cache;
import com.limou.agent.weixin.model.WeixinQrCodeReq;
import com.limou.agent.weixin.model.WeixinQrCodeRes;
import com.limou.agent.weixin.model.WeixinTemplateMessageVO;
import com.limou.agent.weixin.model.WeixinTokenRes;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import retrofit2.Call;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 微信登录服务实现
 */
@Slf4j
@Service
public class WeixinLoginServiceImpl implements IWeixinLoginService {

    @Resource
    private WeixinProperties weixinProperties;

    @Resource
    private Cache<String, String> weixinAccessTokenCache;

    @Resource
    private Cache<String, String> openidTokenCache;

    @Resource
    private IWeixinApiService weixinApiService;

    @Override
    public String createQrCodeTicket() throws Exception {
        // 1. 获取 accessToken
        String accessToken = weixinAccessTokenCache.getIfPresent(weixinProperties.getAppId());
        if (null == accessToken) {
            Call<WeixinTokenRes> call = weixinApiService.getToken("client_credential",
                    weixinProperties.getAppId(), weixinProperties.getAppSecret());
            WeixinTokenRes weixinTokenRes = call.execute().body();
            assert weixinTokenRes != null;
            accessToken = weixinTokenRes.getAccess_token();
            weixinAccessTokenCache.put(weixinProperties.getAppId(), accessToken);
        }

        // 2. 生成 ticket
        WeixinQrCodeReq weixinQrCodeReq = WeixinQrCodeReq.builder()
                .expire_seconds(2592000)
                .action_name(WeixinQrCodeReq.ActionNameTypeVO.QR_SCENE.getCode())
                .action_info(WeixinQrCodeReq.ActionInfo.builder()
                        .scene(WeixinQrCodeReq.ActionInfo.Scene.builder()
                                .scene_id(100601)
                                .build())
                        .build())
                .build();

        Call<WeixinQrCodeRes> call = weixinApiService.createQrCode(accessToken, weixinQrCodeReq);
        WeixinQrCodeRes weixinQrCodeRes = call.execute().body();
        assert null != weixinQrCodeRes;
        return weixinQrCodeRes.getTicket();
    }

    @Override
    public String checkLogin(String ticket) {
        return openidTokenCache.getIfPresent(ticket);
    }

    @Override
    public void saveLoginState(String ticket, String openid) throws IOException {
        openidTokenCache.put(ticket, openid);

        // 1. 获取 accessToken
        String accessToken = weixinAccessTokenCache.getIfPresent(weixinProperties.getAppId());
        if (null == accessToken) {
            Call<WeixinTokenRes> call = weixinApiService.getToken("client_credential",
                    weixinProperties.getAppId(), weixinProperties.getAppSecret());
            WeixinTokenRes weixinTokenRes = call.execute().body();
            assert weixinTokenRes != null;
            accessToken = weixinTokenRes.getAccess_token();
            weixinAccessTokenCache.put(weixinProperties.getAppId(), accessToken);
        }

        // 2. 发送模板消息
        Map<String, Map<String, String>> data = new HashMap<>();
        WeixinTemplateMessageVO.put(data, WeixinTemplateMessageVO.TemplateKey.USER, openid);

        WeixinTemplateMessageVO templateMessageDTO = new WeixinTemplateMessageVO(openid,
                weixinProperties.getTemplateId());
        templateMessageDTO.setUrl("https://gaga.plus");
        templateMessageDTO.setData(data);

        Call<Void> call = weixinApiService.sendMessage(accessToken, templateMessageDTO);
        call.execute();
    }

}
