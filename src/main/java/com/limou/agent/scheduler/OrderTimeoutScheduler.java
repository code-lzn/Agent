package com.limou.agent.scheduler;

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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单超时取消调度器
 * 每 30 秒扫描一次过期订单，自动取消并释放座位
 *
 * 需要在 Spring Boot 启动类上添加 @EnableScheduling 注解
 */
@Slf4j
@Component
public class OrderTimeoutScheduler {

    @Resource
    private OrderMapper orderMapper;

    @Resource
    private OrderSeatMapper orderSeatMapper;

    @Resource
    private SeatMapper seatMapper;

    @Resource
    private RedissonClient redissonClient;

    /**
     * 每 30 秒处理一次过期订单
     */
    @Scheduled(fixedDelay = 30_000)
    @Transactional(rollbackFor = Exception.class)
    public void cancelExpiredOrders() {
        try {
            // 1. 查询所有 pending 状态且已过期的订单
            List<Order> expiredOrders = orderMapper.selectListByQuery(
                    QueryWrapper.create()
                            .eq(Order::getStatus, "pending")
                            .lt(Order::getExpireAt, LocalDateTime.now())
            );

            if (expiredOrders.isEmpty()) {
                return;
            }

            log.info("发现 {} 个过期订单，开始处理...", expiredOrders.size());

            for (Order order : expiredOrders) {
                try {
                    cancelSingleOrder(order);
                } catch (Exception e) {
                    log.error("取消订单 {} 时出错，跳过该订单", order.getId(), e);
                }
            }

            log.info("过期订单处理完成，共处理 {} 个", expiredOrders.size());

        } catch (Exception e) {
            log.error("处理过期订单时出错", e);
        }
    }

    /**
     * 取消单个订单并释放座位
     */
    private void cancelSingleOrder(Order order) {
        Long orderId = order.getId();

        // 2. 查询订单关联的座位
        List<OrderSeat> orderSeats = orderSeatMapper.selectListByQuery(
                QueryWrapper.create().eq(OrderSeat::getOrderId, orderId)
        );

        // 3. 释放座位：将状态从 locked 改为 available
        for (OrderSeat os : orderSeats) {
            seatMapper.updateByQuery(
                    Seat.builder().status("available").build(),
                    QueryWrapper.create()
                            .eq(Seat::getId, os.getSeatId())
                            .eq(Seat::getStatus, "locked")
            );

            // 释放 Redis 分布式锁
            try {
                String lockKey = "seat:lock:" + order.getScheduleId() + ":" + os.getSeatId();
                redissonClient.getLock(lockKey).forceUnlock();
            } catch (Exception e) {
                log.debug("释放 Redis 锁失败（可能已过期）: orderId={}, seatId={}", orderId, os.getSeatId());
            }
        }

        // 4. 更新订单状态为已取消
        orderMapper.update(Order.builder()
                .id(orderId)
                .status("cancelled")
                .cancelReason("timeout")
                .build());

        log.info("订单已超时取消: orderId={}, orderNo={}, 释放 {} 个座位",
                orderId, order.getOrderNo(), orderSeats.size());
    }
}
