package com.limou.agent.ai.movie.tools;

import cn.hutool.json.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limou.agent.mapper.OrderMapper;
import com.limou.agent.mapper.UserPreferenceMapper;
import com.limou.agent.model.entity.Order;
import com.limou.agent.model.entity.UserPreference;
import com.limou.agent.model.enums.OrderStatusEnum;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户偏好工具
 * 获取用户偏好设置和历史订单，用于个性化推荐和"老样子"快捷购票
 */
@Slf4j
@Component
public class GetUserPreferenceTool extends BaseTool {

    @Resource
    private UserPreferenceMapper userPreferenceMapper;

    @Resource
    private OrderMapper orderMapper;

    @Resource
    private ObjectMapper objectMapper;

    @Tool(description = "获取用户偏好设置和购票历史。当用户说'老样子'或需要个性化推荐时调用。返回用户偏好JSON")
    public String getUserPreference(
            @ToolParam(description = "用户ID") Long userId
    ) {
        try {
            if (userId == null) {
                return "{\"preferences\":null,\"message\":\"用户未登录\"}";
            }

            Map<String, Object> result = new HashMap<>();

            // 1. 查询用户偏好设置
            QueryWrapper prefWrapper = QueryWrapper.create()
                    .eq(UserPreference::getUserId, userId);
            UserPreference preference = userPreferenceMapper.selectOneByQuery(prefWrapper);

            if (preference != null) {
                Map<String, Object> prefMap = new HashMap<>();
                prefMap.put("preferredTypes", preference.getPreferredTypes());
                prefMap.put("preferredHallType", preference.getPreferredHallType());
                prefMap.put("budgetMax", preference.getBudgetMax());
                prefMap.put("frequentCinemaId", preference.getFrequentCinemaId());
                prefMap.put("preferredSeatZone", preference.getPreferredSeatZone());
                result.put("preferences", prefMap);
            }

            // 2. 查询最近 5 笔订单，分析购票习惯
            QueryWrapper orderWrapper = QueryWrapper.create()
                    .eq(Order::getUserId, userId)
                    .eq(Order::getStatus, OrderStatusEnum.PAID.getValue())
                    .orderBy(Order::getCreateTime, false)
                    .limit(5);

            List<Order> recentOrders = orderMapper.selectListByQuery(orderWrapper);
            if (!recentOrders.isEmpty()) {
                List<Map<String, Object>> orderList = recentOrders.stream().map(o -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("orderId", o.getId());
                    map.put("filmName", o.getFilmName());
                    map.put("cinemaName", o.getCinemaName());
                    map.put("scheduleTime", o.getScheduleTime());
                    map.put("count", o.getCount());
                    map.put("totalPrice", o.getTotalPrice());
                    return map;
                }).collect(Collectors.toList());
                result.put("recentOrders", orderList);

                // 分析最常去的影院
                Map<String, Long> cinemaCount = recentOrders.stream()
                        .collect(Collectors.groupingBy(
                                o -> o.getCinemaName() != null ? o.getCinemaName() : "未知",
                                Collectors.counting()
                        ));
                String favoriteCinema = cinemaCount.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(null);
                result.put("favoriteCinema", favoriteCinema);

                // 最近一次订单——"老样子"
                Order lastOrder = recentOrders.get(0);
                Map<String, Object> lastOrderInfo = new HashMap<>();
                lastOrderInfo.put("filmName", lastOrder.getFilmName());
                lastOrderInfo.put("cinemaName", lastOrder.getCinemaName());
                lastOrderInfo.put("count", lastOrder.getCount());
                lastOrderInfo.put("totalPrice", lastOrder.getTotalPrice());
                result.put("lastOrder", lastOrderInfo);

                if (preference == null && lastOrder != null) {
                    result.put("message", "您上次看了《" + lastOrder.getFilmName()
                            + "》，在" + lastOrder.getCinemaName()
                            + "，购票" + lastOrder.getCount() + "张。需要按'老样子'来吗？");
                }
            }

            String json = objectMapper.writeValueAsString(result);
            log.info("getUserPreference: userId={}, hasPreference={}, recentOrders={}",
                    userId, preference != null, recentOrders.size());
            return json;

        } catch (Exception e) {
            log.error("getUserPreference 查询失败", e);
            return "{\"preferences\":null,\"recentOrders\":[],\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    @Override
    public String getToolName() {
        return "getUserPreference";
    }

    @Override
    public String getDisplayName() {
        return "获取用户偏好";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        Long userId = arguments.getLong("userId");
        return String.format("[工具调用] 获取用户偏好 userId=%d", userId);
    }
}
