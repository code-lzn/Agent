package com.limou.agent.controller;

import com.limou.agent.config.AlipayConfig;
import com.limou.agent.mq.OrderStatusNotifier;
import com.limou.agent.model.entity.Order;
import com.limou.agent.model.entity.OrderSeat;
import com.limou.agent.model.enums.OrderStatusEnum;
import com.limou.agent.model.entity.Seat;
import com.limou.agent.service.AlipayService;
import com.limou.agent.service.OrderSeatService;
import com.limou.agent.service.OrderService;
import com.limou.agent.service.SeatLockService;
import com.limou.agent.service.SeatService;
import com.limou.agent.service.TicketService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 支付宝沙箱回调。
 *
 * @author 李振南
 */
@RestController
@RequestMapping("/payment/alipay")
@Slf4j
public class AlipayController {

    @Autowired
    private AlipayService alipayService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderSeatService orderSeatService;

    @Autowired
    private SeatService seatService;

    @Autowired
    private SeatLockService seatLockService;

    @Autowired
    private OrderStatusNotifier orderStatusNotifier;

    @Autowired
    private AlipayConfig alipayConfig;

    /**
     * 从 YAML 配置 alipay.return_url 中提取前端 base URL，无硬编码。
     * 例：return_url = http://192.168.13.1:8000/home → 返回 http://192.168.13.1:8000
     */
    private String frontendBaseUrl() {
        String returnUrl = alipayConfig.getReturnUrl();
        int schemeEnd = returnUrl.indexOf("://");
        if (schemeEnd < 0) return returnUrl;
        String afterScheme = returnUrl.substring(schemeEnd + 3);
        int pathStart = afterScheme.indexOf('/');
        return pathStart > 0
                ? returnUrl.substring(0, schemeEnd + 3 + pathStart)
                : returnUrl;
    }

    @Autowired
    private TicketService ticketService;

    /**
     * 浏览器直接打开即可跳转支付宝沙箱收银台。
     * 用法：http://localhost:8123/api/payment/alipay/pay?orderId=xxx
     */

    @GetMapping(value = "/pay", produces = "text/html;charset=UTF-8")
    public String payPage(@RequestParam Long orderId) {
        Order order = orderService.getById(orderId);
        if (order == null || !OrderStatusEnum.PENDING.getValue().equals(order.getStatus())) {
            return "<h1>订单不存在或状态异常</h1>";
        }
        String subject = order.getFilmName() + " - 电影票";
        String totalAmount = order.getTotalPrice().toString();
        return alipayService.createPayPage(order.getOrderNo(), totalAmount, subject);
    }

    /**
     * 支付宝沙箱同步返回。
     * 用户支付完成后支付宝跳转到 return_url。
     * 同步回调直接更新订单状态（不等异步通知），然后重定向到前端支付成功页。
     */
    @GetMapping("/return")
    @Transactional(rollbackFor = Exception.class)
    public String returnPage(@RequestParam String out_trade_no,
            @RequestParam(required = false) String trade_no,
            @RequestParam(required = false) String total_amount) {
        try {
            QueryWrapper qw = QueryWrapper.create().eq("orderNo", out_trade_no);
            Order order = orderService.getOne(qw);
            if (order == null) {
                return "<script>window.location.replace('" + frontendBaseUrl() + "');</script>";
            }

            // 异步通知可能已经处理过了，避免重复
            if (!OrderStatusEnum.PAID.getValue().equals(order.getStatus())) {
                handlePaymentSuccess(order, trade_no);
            }

            return "<script>window.location.replace('" + frontendBaseUrl() + "');</script>";
        } catch (Exception e) {
            log.error("同步回调处理异常: out_trade_no={}", out_trade_no, e);
            return "<script>window.location.replace('" + frontendBaseUrl() + "');</script>";
        }
    }

    /**
     * 支付宝沙箱异步通知。
     * 支付宝在用户支付完成后，会向 notifyUrl 发送 POST 请求。
     */
    @PostMapping("/notify")
    @Transactional(rollbackFor = Exception.class)
    public String notify(HttpServletRequest request) {
        try {
            Map<String, String> params = new HashMap<>();
            Map<String, String[]> requestParams = request.getParameterMap();
            for (Map.Entry<String, String[]> entry : requestParams.entrySet()) {
                String name = entry.getKey();
                String[] values = entry.getValue();
                String value = String.join(",", values);
                params.put(name, value);
            }
            log.info("收到支付宝异步通知: {}", params);

            boolean verifyResult = alipayService.verifyNotify(params);
            if (!verifyResult) {
                log.warn("支付宝通知签名验证失败");
                return "failure";
            }

            String tradeStatus = params.get("trade_status");
            String outTradeNo = params.get("out_trade_no");
            String tradeNo = params.get("trade_no");

            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                QueryWrapper qw = QueryWrapper.create().eq("orderNo", outTradeNo);
                Order order = orderService.getOne(qw);
                if (order == null) {
                    log.warn("订单不存在: {}", outTradeNo);
                    return "failure";
                }

                if (OrderStatusEnum.PAID.getValue().equals(order.getStatus())) {
                    log.info("订单已支付，忽略重复通知: {}", outTradeNo);
                    return "success";
                }

                handlePaymentSuccess(order, tradeNo);
                log.info("支付宝异步通知处理成功，订单号: {}, 交易号: {}", outTradeNo, tradeNo);
            }

            return "success";
        } catch (Exception e) {
            log.error("支付宝异步通知处理异常", e);
            return "failure";
        }
    }

    /**
     * 支付成功通用处理：更新订单状态 + 座位标记已售 + 清理 Redis 锁。
     */
    private void handlePaymentSuccess(Order order, String tradeNo) {
        order.setStatus(OrderStatusEnum.PAID.getValue());
        order.setPaidAt(LocalDateTime.now());
        order.setAlipayTradeNo(tradeNo);
        order.setAlipayStatus("TRADE_SUCCESS");
        orderService.updateById(order);

        // 更新座位为已售
        QueryWrapper sqw = QueryWrapper.create().eq("orderId", order.getId());
        List<OrderSeat> orderSeats = orderSeatService.list(sqw);
        List<Long> seatIds = orderSeats.stream().map(OrderSeat::getSeatId).toList();
        if (!seatIds.isEmpty()) {
            List<Seat> seats = seatService.listByIds(seatIds);
            seats.forEach(s -> s.setStatus("sold"));
            seatService.updateBatch(seats);

            // 清理 Redis 锁集合
            seatLockService.releaseSeats(order.getScheduleId(), seatIds);
        }

        // 通过 SSE 推送通知前端
        orderStatusNotifier.notifyOrderPaid(order.getUserId(), order.getId());
        log.info("订单 {} 支付成功 SSE 已推送", order.getId());
    }
}
