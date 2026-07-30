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
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 支付宝回调 控制层。
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
     * 支付宝沙箱异步通知。
     * 支付宝在用户支付完成后，会向 notifyUrl 发送 POST 请求。
     */
    @PostMapping("/notify")
    public void notify(HttpServletRequest request, HttpServletResponse response) {
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
                response.getWriter().println("failure");
                return;
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
                    response.getWriter().println("failure");
                    return;
                }

                // 防止重复处理
                if ("paid".equals(order.getStatus())) {
                    response.getWriter().println("success");
                    return;
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
            response.setContentType("text/plain");
            response.getWriter().println("success");

        } catch (Exception e) {
            log.error("支付宝异步通知处理异常", e);
            try {
                response.setContentType("text/plain");
                response.getWriter().println("failure");
            } catch (Exception ex) {
                // ignore
            }
        }
    }
}
