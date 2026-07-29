package com.limou.agent.ai.movie.tools;

import cn.hutool.json.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limou.agent.ai.tools.BaseTool;
import com.limou.agent.mapper.OrderMapper;
import com.limou.agent.mapper.OrderSeatMapper;
import com.limou.agent.mapper.SeatMapper;
import com.limou.agent.model.entity.Order;
import com.limou.agent.model.entity.OrderSeat;
import com.limou.agent.model.entity.Seat;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 支付工具
 * 模拟支付流程，将订单状态改为已支付，座位状态改为已售
 */
@Slf4j
@Component
public class PayOrderTool extends BaseTool {

    @Resource
    private OrderMapper orderMapper;

    @Resource
    private OrderSeatMapper orderSeatMapper;

    @Resource
    private SeatMapper seatMapper;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private ObjectMapper objectMapper;

    @Tool(description = "模拟支付订单。传入订单ID和支付方式。返回支付结果JSON")
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
                // 订单已过期，取消
                orderMapper.update(Order.builder()
                        .id(orderId)
                        .status("cancelled")
                        .cancelReason("timeout")
                        .build());
                return "{\"success\":false,\"error\":\"订单已超时取消，请重新选座下单\"}";
            }

            // 3. 更新订单状态
            String method = (payMethod == null || payMethod.isBlank()) ? "alipay" : payMethod;
            LocalDateTime now = LocalDateTime.now();

            orderMapper.update(Order.builder()
                    .id(orderId)
                    .status("paid")
                    .alipayTradeNo("SIM" + System.currentTimeMillis())
                    .alipayStatus("TRADE_SUCCESS")
                    .paidAt(now)
                    .build());

            // 4. 更新座位状态：locked → sold
            List<OrderSeat> orderSeats = orderSeatMapper.selectListByQuery(
                    QueryWrapper.create().eq(OrderSeat::getOrderId, orderId)
            );

            for (OrderSeat os : orderSeats) {
                seatMapper.update(Seat.builder()
                        .id(os.getSeatId())
                        .status("sold")
                        .build());

                // 释放 Redis 座位锁
                String lockKey = "seat:lock:" + order.getScheduleId() + ":" + os.getSeatId();
                try {
                    redissonClient.getLock(lockKey).forceUnlock();
                } catch (Exception ignored) {}
            }

            // 5. 构建返回
            List<String> seatLabels = orderSeats.stream()
                    .map(OrderSeat::getSeatLabel)
                    .collect(Collectors.toList());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("orderId", orderId);
            result.put("orderNo", order.getOrderNo());
            result.put("payMethod", method);
            result.put("paidAt", now.toString());
            result.put("filmName", order.getFilmName());
            result.put("cinemaName", order.getCinemaName());
            result.put("scheduleTime", order.getScheduleTime());
            result.put("seatLabels", seatLabels);
            result.put("totalPrice", order.getTotalPrice());
            result.put("message", "支付成功！🎬 " + order.getFilmName()
                    + " " + String.join("、", seatLabels)
                    + "，祝您观影愉快～🍿");

            log.info("payOrder 成功: orderNo={}, film={}, amount={}",
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
