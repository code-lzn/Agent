package com.limou.agent.controller;

import com.limou.agent.model.entity.Order;
import com.limou.agent.model.entity.OrderSeat;
import com.limou.agent.model.entity.Seat;
import com.limou.agent.service.AlipayService;
import com.limou.agent.service.OrderSeatService;
import com.limou.agent.service.OrderService;
import com.limou.agent.service.SeatService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    /**
     * 浏览器直接打开即可跳转支付宝沙箱收银台。
     * 用法：http://localhost:8123/api/payment/alipay/pay?orderId=xxx
     */

    @GetMapping(value = "/pay", produces = "text/html;charset=UTF-8")
    public String payPage(@RequestParam Long orderId) {
        Order order = orderService.getById(orderId);
        if (order == null || !"pending".equals(order.getStatus())) {
            return "<h1>订单不存在或状态异常</h1>";
        }
        String subject = order.getFilmName() + " - 电影票";
        String totalAmount = order.getTotalPrice().toString();
        return alipayService.createPayPage(order.getOrderNo(), totalAmount, subject);
    }

    /**
     * 支付宝沙箱异步通知。
     * 支付宝在用户支付完成后，会向 notifyUrl 发送 POST 请求。
     * 路径：POST /api/payment/alipay/notify
     */
    @PostMapping("/notify")
    public String notify(HttpServletRequest request) {
        try {
            // 1. 获取所有参数
            Map<String, String> params = new HashMap<>();
            Map<String, String[]> requestParams = request.getParameterMap();
            for (Map.Entry<String, String[]> entry : requestParams.entrySet()) {
                String name = entry.getKey();
                String[] values = entry.getValue();
                String value = String.join(",", values);
                params.put(name, value);
            }
            log.info("收到支付宝异步通知: {}", params);

            // 2. 验证签名
            boolean verifyResult = alipayService.verifyNotify(params);
            if (!verifyResult) {
                log.warn("支付宝通知签名验证失败");
                return "failure";
            }

            // 3. 处理业务逻辑
            String tradeStatus = params.get("trade_status");
            String outTradeNo = params.get("out_trade_no");
            String tradeNo = params.get("trade_no"); // 支付宝交易号

            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                // 查询订单
                QueryWrapper qw = QueryWrapper.create().eq("orderNo", outTradeNo);
                Order order = orderService.getOne(qw);
                if (order == null) {
                    log.warn("订单不存在: {}", outTradeNo);
                    return "failure";
                }

                // 防止重复处理
                if ("paid".equals(order.getStatus())) {
                    log.info("订单已支付，忽略重复通知: {}", outTradeNo);
                    return "success";
                }

                // 更新订单状态
                order.setStatus("paid");
                order.setPaidAt(LocalDateTime.now());
                order.setAlipayTradeNo(tradeNo);
                order.setAlipayStatus(tradeStatus);
                orderService.updateById(order);

                // 更新座位为已售
                QueryWrapper sqw = QueryWrapper.create().eq("orderId", order.getId());
                List<OrderSeat> orderSeats = orderSeatService.list(sqw);
                List<Long> seatIds = orderSeats.stream().map(OrderSeat::getSeatId).toList();
                if (!seatIds.isEmpty()) {
                    List<Seat> seats = seatService.listByIds(seatIds);
                    seats.forEach(s -> s.setStatus("sold"));
                    seatService.updateBatch(seats);
                }

                log.info("支付宝异步通知处理成功，订单号: {}, 交易号: {}", outTradeNo, tradeNo);
            }

            // 4. 返回 success 给支付宝
            return "success";

        } catch (Exception e) {
            log.error("支付宝异步通知处理异常", e);
            return "failure";
        }
    }
}
