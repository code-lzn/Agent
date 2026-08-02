package com.limou.agent.weixin;

import com.limou.agent.weixin.model.WeixinQrCodeReq;
import com.limou.agent.weixin.model.WeixinQrCodeRes;
import com.limou.agent.weixin.model.WeixinTemplateMessageVO;
import com.limou.agent.weixin.model.WeixinTokenRes;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * 微信 API 服务 Retrofit2 接口
 */
public interface IWeixinApiService {

    /**
     * 获取 Access token
     * 文档：https://developers.weixin.qq.com/doc/offiaccount/Basic_Information/Get_access_token.html
     *
     * @param grantType 获取access_token填写client_credential
     * @param appId     第三方用户唯一凭证
     * @param appSecret 第三方用户唯一凭证密钥，即appsecret
     * @return 响应结果
     */
    @GET("cgi-bin/token")
    Call<WeixinTokenRes> getToken(@Query("grant_type") String grantType,
                                  @Query("appid") String appId,
                                  @Query("secret") String appSecret);

    /**
     * 获取凭据 ticket（用于生成带参数二维码）
     * 文档：https://developers.weixin.qq.com/doc/offiaccount/Account_Management/Generating_a_Parametric_QR_Code.html
     * 前端根据凭证展示二维码：https://mp.weixin.qq.com/cgi-bin/showqrcode?ticket=TICKET
     *
     * @param accessToken     getToken 获取的 token 信息
     * @param weixinQrCodeReq 入参对象
     * @return 应答结果
     */
    @POST("cgi-bin/qrcode/create")
    Call<WeixinQrCodeRes> createQrCode(@Query("access_token") String accessToken, @Body WeixinQrCodeReq weixinQrCodeReq);

    /**
     * 发送微信公众号模板消息
     * 文档：https://mp.weixin.qq.com/debug/cgi-bin/readtmpl?t=tmplmsg/faq_tmpl
     *
     * @param accessToken              getToken 获取的 token 信息
     * @param weixinTemplateMessageVO  入参对象
     * @return 应答结果
     */
    @POST("cgi-bin/message/template/send")
    Call<Void> sendMessage(@Query("access_token") String accessToken, @Body WeixinTemplateMessageVO weixinTemplateMessageVO);

}
