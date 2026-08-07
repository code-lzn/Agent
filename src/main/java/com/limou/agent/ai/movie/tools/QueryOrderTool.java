package com.limou.agent.ai.movie.tools;

import cn.hutool.json.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limou.agent.ai.movie.ConversationContext;
import com.limou.agent.ai.movie.MovieStateManager;
import com.limou.agent.model.dto.movie.ConversationState;
import com.limou.agent.model.vo.OrderVO;
import com.limou.agent.service.OrderService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 订单查询工具 — 用户问"看看我的订单""订单详情"时调用
 */
@Slf4j
@Component
public class QueryOrderTool extends BaseTool {

    @Resource
    private OrderService orderService;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private MovieStateManager stateManager;

    @Tool(description = "查询订单详情。用户问：看看订单，我的订单，订单怎么样了，查一下订单，支付成功了吗，时调用。返回订单完整信息。")
    public String queryOrder(
            @ToolParam(description = "订单ID，从对话状态中获取") Long orderId,
            @ToolParam(description = "用户ID") Long userId
    ) {
        try {
            OrderVO vo = orderService.getOrderDetail(orderId, userId);
            if (vo == null) {
                return "{\"success\":false,\"error\":\"订单不存在\"}";
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("orderId", vo.getId());
            result.put("orderNo", vo.getOrderNo());
            result.put("filmName", vo.getFilmName());
            result.put("cinemaName", vo.getCinemaName());
            result.put("hallName", vo.getHallName());
            result.put("scheduleTime", vo.getScheduleTime() != null ? vo.getScheduleTime().toString() : null);
            result.put("totalPrice", vo.getTotalPrice());
            result.put("count", vo.getCount());
            result.put("status", vo.getStatus());
            result.put("seatLabels", vo.getSeatLabels());
            result.put("posterUrl", vo.getPosterUrl());
            result.put("createTime", vo.getCreateTime() != null ? vo.getCreateTime().toString() : null);
            result.put("expireAt", vo.getExpireAt() != null ? vo.getExpireAt().toString() : null);

            // ★ 写回 ConversationState
            String convId = ConversationContext.get();
            if (convId != null) {
                try {
                    ConversationState state = stateManager.getState(convId);
                    boolean isDead = "cancelled".equals(vo.getStatus())
                            || "refunded".equals(vo.getStatus())
                            || "expired".equals(vo.getStatus());
                    if (isDead) {
                        // 订单已失效 → 清掉残留，让用户可以重新选座下单
                        state.setOrderId(null);
                        state.setSeatIds(null);
                        state.setSeatLabels(null);
                        log.info("QueryOrder 订单已失效({})，清除状态残留: orderId={}",
                                vo.getStatus(), orderId);
                    } else {
                        if (vo.getFilmName() != null) state.setFilmName(vo.getFilmName());
                        if (vo.getCinemaName() != null) state.setCinemaName(vo.getCinemaName());
                        if (vo.getScheduleId() != null) state.setScheduleId(vo.getScheduleId());
                        if (vo.getHallName() != null) state.setHallName(vo.getHallName());
                        if (vo.getSeatLabels() != null && !vo.getSeatLabels().isEmpty())
                            state.setSeatLabels(vo.getSeatLabels());
                        if (vo.getTotalPrice() != null) state.setTotalPrice(vo.getTotalPrice());
                        if (vo.getCount() != null) state.setTicketCount(vo.getCount());
                    }
                    stateManager.saveState(convId, state);
                    log.info("QueryOrder 写回状态: film={}, cinema={}, price={}, status={}",
                            vo.getFilmName(), vo.getCinemaName(), vo.getTotalPrice(), vo.getStatus());
                } catch (Exception e) {
                    log.warn("QueryOrder 写回状态失败", e);
                }
            }

            log.info("QueryOrder: orderId={}, film={}, cinema={}, status={}",
                    orderId, vo.getFilmName(), vo.getCinemaName(), vo.getStatus());
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("QueryOrder 失败: orderId={}", orderId, e);
            return "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    @Override
    public String getToolName() {
        return "queryOrder";
    }

    @Override
    public String getDisplayName() {
        return "查询订单";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        return "[工具调用] 查询订单详情";
    }
}
