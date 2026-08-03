package com.limou.agent.ai.movie.tools;

import cn.hutool.json.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limou.agent.ai.tools.BaseTool;
import com.limou.agent.mapper.OrderMapper;
import com.limou.agent.mapper.OrderSeatMapper;
import com.limou.agent.model.entity.Order;
import com.limou.agent.model.entity.OrderSeat;
import com.limou.agent.service.AlipayService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 支付工具
 * 对接支付宝沙箱支付，生成真实支付页面
 */
@Slf4j
@Component
public class PayOrderTool extends BaseTool {

    @Resource
    private OrderMapper orderMapper;

    @Resource
    private OrderSeatMapper orderSeatMapper;

    @Resource
    private AlipayService alipayService;

    @Resource
    private ObjectMapper objectMapper;

    @Tool(description = "支付订单。传入订单ID和支付方式。返回支付宝支付页面HTML")
    @Transactional(rollbackFor = Exception.class)
    public String payOrder(
            @ToolParam(description = "订单ID") Long orderId,
            @ToolParam(description = "支付方式: alipay/wechat，默认alipay") String payMethod
    ) {
        try {
            // 1. 查询订单
            Order order = orderMapper.selectOneById(orderId);
            if (order == null) {
                return "{\"success\":false,\"error\":\"订单不存在\"}";
            }

            if (!"pending".equals(order.getStatus())) {
                return "{\"success\":false,\"error\":\"订单状态异常（" + order.getStatus() + "），无法支付\"}";
            }

            // 2. 检查是否已过期
            if (order.getExpireAt() != null && order.getExpireAt().isBefore(LocalDateTime.now())) {
                orderMapper.update(Order.builder()
                        .id(orderId)
                        .status("cancelled")
                        .cancelReason("timeout")
                        .build());
                return "{\"success\":false,\"error\":\"订单已超时取消，请重新选座下单\"}";
            }

            // 3. 使用 AlipayService 生成真实支付页面
            String method = (payMethod == null || payMethod.isBlank()) ? "alipay" : payMethod;

            if (!"alipay".equalsIgnoreCase(method)) {
                return "{\"success\":false,\"error\":\"暂仅支持支付宝支付\"}";
            }

            // 查询座位信息
            List<OrderSeat> orderSeats = orderSeatMapper.selectListByQuery(
                    QueryWrapper.create().eq(OrderSeat::getOrderId, orderId)
            );
            List<String> seatLabels = orderSeats.stream()
                    .map(OrderSeat::getSeatLabel)
                    .collect(Collectors.toList());

            // 调用支付宝沙箱生成支付页面
            String subject = order.getFilmName() + " - 电影票";
            String totalAmount = order.getTotalPrice().toString();
            String payForm = alipayService.createPayPage(order.getOrderNo(), totalAmount, subject);

            // 4. 构建返回
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("orderId", orderId);
            result.put("orderNo", order.getOrderNo());
            result.put("payMethod", method);
            result.put("payForm", payForm);
            result.put("filmName", order.getFilmName());
            result.put("cinemaName", order.getCinemaName());
            result.put("scheduleTime", order.getScheduleTime());
            result.put("seatLabels", seatLabels);
            result.put("totalPrice", order.getTotalPrice());
            result.put("message", "请使用支付宝完成支付 " + order.getTotalPrice() + " 元，支付完成后订单将自动确认。");

            log.info("payOrder 生成支付宝支付页面: orderNo={}, film={}, amount={}",
                    order.getOrderNo(), order.getFilmName(), order.getTotalPrice());
            return objectMapper.writeValueAsString(result);

        } catch (Exception e) {
            log.error("payOrder 失败", e);
            return "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    @Override
    public String getToolName() {
        return "payOrder";
    }

    @Override
    public String getDisplayName() {
        return "支付订单";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        Long orderId = arguments.getLong("orderId");
        return String.format("[工具调用] 支付订单 orderId=%d", orderId);
    }
}
