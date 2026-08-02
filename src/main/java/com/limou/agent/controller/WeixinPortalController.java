package com.limou.agent.controller;

import com.limou.agent.common.BaseResponse;
import com.limou.agent.common.ResultUtils;
import com.limou.agent.weixin.IWeixinLoginService;
import com.limou.agent.weixin.WeixinProperties;
import com.limou.agent.weixin.model.MessageTextEntity;
import com.limou.agent.weixin.util.SignatureUtil;
import com.limou.agent.weixin.util.XmlUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 微信公众号对接 Portal
 * <p>
 * 对接地址示例：http://lbljh.nat100.top/api/v1/weixin/portal/receive
 */
@Slf4j
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
@RequestMapping("/v1/weixin/portal")
public class WeixinPortalController {

    @Resource
    private WeixinProperties weixinProperties;

    @Resource
    private IWeixinLoginService weixinLoginService;

    /**
     * 微信公众号验签（GET 请求）
     */
    @GetMapping(value = "/receive", produces = "text/plain;charset=utf-8")
    public String validate(@RequestParam(value = "signature", required = false) String signature,
                           @RequestParam(value = "timestamp", required = false) String timestamp,
                           @RequestParam(value = "nonce", required = false) String nonce,
                           @RequestParam(value = "echostr", required = false) String echostr) {
        try {
            log.info("微信公众号验签信息开始 [{}, {}, {}, {}]", signature, timestamp, nonce, echostr);
            if (StringUtils.isAnyBlank(signature, timestamp, nonce, echostr)) {
                throw new IllegalArgumentException("请求参数非法，请核实!");
            }
            boolean check = SignatureUtil.check(weixinProperties.getToken(), signature, timestamp, nonce);
            log.info("微信公众号验签信息完成 check：{}", check);
            if (!check) {
                return null;
            }
            return echostr;
        } catch (Exception e) {
            log.error("微信公众号验签信息失败 [{}, {}, {}, {}]", signature, timestamp, nonce, echostr, e);
            return null;
        }
    }

    /**
     * 接收微信公众号消息/事件推送（POST 请求）
     */
    @PostMapping(value = "/receive", produces = "application/xml; charset=UTF-8")
    public String post(@RequestBody String requestBody,
                       @RequestParam("signature") String signature,
                       @RequestParam("timestamp") String timestamp,
                       @RequestParam("nonce") String nonce,
                       @RequestParam("openid") String openid,
                       @RequestParam(name = "encrypt_type", required = false) String encType,
                       @RequestParam(name = "msg_signature", required = false) String msgSignature) {
        try {
            log.info("接收微信公众号信息请求{}开始 {}", openid, requestBody);
            // 消息转换
            MessageTextEntity message = XmlUtil.xmlToBean(requestBody, MessageTextEntity.class);

            if ("event".equals(message.getMsgType()) && "SCAN".equals(message.getEvent())) {
                weixinLoginService.saveLoginState(message.getTicket(), openid);
                return buildMessageTextEntity(openid, "登录成功");
            }

            return buildMessageTextEntity(openid, "你好，" + message.getContent());
        } catch (Exception e) {
            log.error("接收微信公众号信息请求{}失败 {}", openid, requestBody, e);
            return "";
        }
    }

    // ==================== 前端调用接口 ====================

    /**
     * 生成微信扫码登录二维码 ticket
     * 前端拿到 ticket 后拼出二维码：https://mp.weixin.qq.com/cgi-bin/showqrcode?ticket={ticket}
     */
    @GetMapping("/createQrCode")
    public BaseResponse<Map<String, String>> createQrCode() {
        try {
            String ticket = weixinLoginService.createQrCodeTicket();
            Map<String, String> result = new HashMap<>();
            result.put("ticket", ticket);
            // 方便前端直接使用
            result.put("qrCodeUrl", "https://mp.weixin.qq.com/cgi-bin/showqrcode?ticket=" + ticket);
            return ResultUtils.success(result);
        } catch (Exception e) {
            log.error("生成微信二维码失败", e);
            throw new RuntimeException("生成微信二维码失败: " + e.getMessage());
        }
    }

    /**
     * 前端轮询检查微信扫码状态
     * @param ticket 二维码 ticket
     * @return openid（已扫码）或 null（未扫码）
     */
    @GetMapping("/checkLogin")
    public BaseResponse<Map<String, Object>> checkLogin(@RequestParam("ticket") String ticket) {
        String openid = weixinLoginService.checkLogin(ticket);
        Map<String, Object> result = new HashMap<>();
        result.put("scanned", openid != null);
        result.put("openid", openid);
        return ResultUtils.success(result);
    }

    /**
     * 构建文本回复消息
     */
    private String buildMessageTextEntity(String openid, String content) {
        MessageTextEntity res = new MessageTextEntity();
        // 公众号分配的ID
        res.setFromUserName(weixinProperties.getOriginalid());
        res.setToUserName(openid);
        res.setCreateTime(String.valueOf(System.currentTimeMillis() / 1000L));
        res.setMsgType("text");
        res.setContent(content);
        return XmlUtil.beanToXml(res);
    }

}
