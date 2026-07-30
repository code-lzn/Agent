package com.limou.agent.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.limou.agent.config.AlipayConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 支付宝沙箱支付服务。
 *
 * @author 李振南
 */
@Service
@Slf4j
public class AlipayService {

    @Autowired
    private AlipayConfig alipayConfig;

    private AlipayClient alipayClient;

    @PostConstruct
    public void init() {
        alipayClient = new DefaultAlipayClient(
                alipayConfig.getGatewayUrl(),
                alipayConfig.getAppId(),
                alipayConfig.getAppPrivateKey(),
                alipayConfig.getFormat(),
                alipayConfig.getCharset(),
                alipayConfig.getAlipayPublicKey(),
                alipayConfig.getSignType()
        );
        log.info("支付宝沙箱客户端初始化完成");
    }

    /**
     * 创建支付页面（返回自动提交的HTML表单）。
     *
     * @param orderNo    订单号
     * @param totalAmount 支付金额（元）
     * @param subject    商品标题
     * @return HTML表单字符串
     */
    public String createPayPage(String orderNo, String totalAmount, String subject) {
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        // 异步通知地址
        request.setNotifyUrl(alipayConfig.getNotifyUrl());
        // 同步跳转地址
        request.setReturnUrl(alipayConfig.getReturnUrl());

        // 构建业务参数（使用 hutool JSONObject，自动处理特殊字符转义）
        cn.hutool.json.JSONObject bizContent = new cn.hutool.json.JSONObject();
        bizContent.set("out_trade_no", orderNo);
        bizContent.set("total_amount", totalAmount);
        bizContent.set("subject", subject);
        bizContent.set("product_code", "FAST_INSTANT_TRADE_PAY");
        request.setBizContent(bizContent.toString());

        try {
            String form = alipayClient.pageExecute(request).getBody();
            log.info("支付宝支付表单生成成功，订单号: {}", orderNo);
            return form;
        } catch (AlipayApiException e) {
            log.error("支付宝支付表单生成失败", e);
            throw new RuntimeException("支付宝支付请求失败: " + e.getErrMsg());
        }
    }

    /**
     * 验证支付宝异步通知签名。
     *
     * @param params 通知参数Map
     * @return 是否验证通过
     */
    public boolean verifyNotify(Map<String, String> params) {
        try {
            boolean result = AlipaySignature.rsaCheckV1(
                    params,
                    alipayConfig.getAlipayPublicKey(),
                    alipayConfig.getCharset(),
                    alipayConfig.getSignType()
            );
            return result;
        } catch (AlipayApiException e) {
            log.error("支付宝通知签名验证失败", e);
            return false;
        }
    }
}
