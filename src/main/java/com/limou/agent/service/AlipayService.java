package com.limou.agent.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeRefundResponse;
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

    /**
     * 支付宝沙箱退款。
     */
    public boolean refund(String orderNo, String refundAmount, String tradeNo) {
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        cn.hutool.json.JSONObject bizContent = new cn.hutool.json.JSONObject();
        // 有真实交易号时优先用 trade_no 精确定位（沙箱交易可能因精度/路径不同导致 out_trade_no 对不上）
        if (tradeNo != null && !tradeNo.isEmpty()) {
            bizContent.set("trade_no", tradeNo);
        } else {
            bizContent.set("out_trade_no", orderNo);
        }
        bizContent.set("refund_amount", refundAmount);
        bizContent.set("out_request_no", orderNo + "_refund");
        request.setBizContent(bizContent.toString());
        try {
            AlipayTradeRefundResponse response = alipayClient.execute(request);
            if (response.isSuccess() && "10000".equals(response.getCode())) {
                log.info("支付宝退款成功，订单号: {}, 退款金额: {}", orderNo, refundAmount);
                return true;
            }
            // 交易不存在（沙箱/模拟支付无真实交易）：无实际资金可退，视为可继续本地退款
            if ("ACQ.TRADE_NOT_EXIST".equals(response.getSubCode())) {
                log.warn("支付宝交易不存在（订单号: {}），视为无真实交易，继续本地退款", orderNo);
                return true;
            }
            log.error("支付宝退款失败，订单号: {}, code: {}, sub_code: {}, msg: {}",
                    orderNo, response.getCode(), response.getSubCode(), response.getMsg());
            return false;
        } catch (AlipayApiException e) {
            log.error("支付宝退款异常，订单号: {}", orderNo, e);
            throw new RuntimeException("支付宝退款请求失败: " + e.getErrMsg());
        }
    }
}
