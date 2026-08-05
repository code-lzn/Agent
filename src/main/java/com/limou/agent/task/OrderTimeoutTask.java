package com.limou.agent.task;

import com.limou.agent.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 订单超时定时任务（每30秒扫描一次）。
 *
 * @author 李振南
 */
@Component
@Slf4j
public class OrderTimeoutTask {

    @Autowired
    private OrderService orderService;

    /**
     * 每30秒执行一次，取消超时未支付订单并释放座位。
     */
    @Scheduled(fixedRate = 30000)
    public void cancelTimeoutOrders() {
        int count = orderService.cancelTimeoutOrders();
        if (count > 0) {
            log.info("超时订单定时任务：已取消 {} 个订单", count);
        }
    }

}
