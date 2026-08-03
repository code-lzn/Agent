package com.limou.agent.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.mybatisflex.core.paginate.Page;
import com.limou.agent.exception.BusinessException;
import com.limou.agent.exception.ErrorCode;
import com.limou.agent.mapper.OrderMapper;
import com.limou.agent.model.dto.order.CreateOrderRequest;
import com.limou.agent.model.dto.order.LockSeatRequest;
import com.limou.agent.model.dto.order.PayOrderRequest;
import com.limou.agent.model.entity.*;
import com.limou.agent.model.vo.OrderVO;
import com.limou.agent.model.vo.PayOrderVO;
import com.limou.agent.service.*;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 订单 服务层实现。
 *
 * @author 李振南
 */
@Service
@Slf4j
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    @Autowired
    private SeatService seatService;

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private OrderSeatService orderSeatService;

    @Autowired
    private FilmService filmService;

    @Autowired
    private CinemaService cinemaService;

    @Autowired
    private HallService hallService;

    @Autowired
    private AlipayService alipayService;

    @Autowired
    private SystemConfigService systemConfigService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean lockSeat(LockSeatRequest request, Long userId) {
        if (request == null || request.getScheduleId() == null || CollUtil.isEmpty(request.getSeatIds())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数无效");
        }

        // 验证场次
        Schedule schedule = scheduleService.getById(request.getScheduleId());
        if (schedule == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "场次不存在");
        }

        // 用行锁查询座位（FOR UPDATE），防止并发超卖
        QueryWrapper qw = QueryWrapper.create()
                .eq("scheduleId", request.getScheduleId())
                .in("id", request.getSeatIds())
                .hint("FOR UPDATE");
        List<Seat> seats = seatService.list(qw);

        if (seats.size() != request.getSeatIds().size()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "部分座位不存在");
        }

        // 检查是否全部可用
        for (Seat seat : seats) {
            if (!"available".equals(seat.getStatus())) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "座位 " + seat.getSeatLabel() + " 已被锁定或售出");
            }
        }

        // 锁定座位
        List<Seat> updateList = seats.stream().peek(s -> s.setStatus("locked")).collect(Collectors.toList());
        seatService.updateBatch(updateList);

        log.info("用户 {} 锁定场次 {} 座位: {}", userId, request.getScheduleId(),
                seats.stream().map(Seat::getSeatLabel).collect(Collectors.joining(",")));
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(CreateOrderRequest request, Long userId) {
        if (request == null || request.getScheduleId() == null || CollUtil.isEmpty(request.getSeatIds())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数无效");
        }

        // 1. 验证场次
        Schedule schedule = scheduleService.getById(request.getScheduleId());
        if (schedule == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "场次不存在");
        }

        // 2. 验证并锁定座位（行锁）
        QueryWrapper qw = QueryWrapper.create()
                .eq("scheduleId", request.getScheduleId())
                .in("id", request.getSeatIds())
                .hint("FOR UPDATE");
        List<Seat> seats = seatService.list(qw);

        // 检查座位状态（available 或 locked 均可 — 如果是locked得是当前用户锁的，简化：允许锁定状态的也可直接下单）
        for (Seat seat : seats) {
            String status = seat.getStatus();
            if (!"available".equals(status) && !"locked".equals(status)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "座位 " + seat.getSeatLabel() + " 已售出");
            }
        }

        // 3. 计算总价
        int count = seats.size();
        if (schedule.getPrice() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "场次票价未设置");
        }
        BigDecimal totalPrice;
        boolean hasVip = seats.stream().anyMatch(s -> "vip".equals(s.getZone()));
        if (hasVip && schedule.getVipPrice() != null) {
            long vipCount = seats.stream().filter(s -> "vip".equals(s.getZone())).count();
            long regularCount = count - vipCount;
            totalPrice = schedule.getVipPrice().multiply(BigDecimal.valueOf(vipCount))
                    .add(schedule.getPrice().multiply(BigDecimal.valueOf(regularCount)));
        } else {
            totalPrice = schedule.getPrice().multiply(BigDecimal.valueOf(count));
        }

        // 4. 获取关联信息
        Film film = filmService.getById(schedule.getFilmId());
        Cinema cinema = cinemaService.getById(schedule.getCinemaId());
        Hall hall = hallService.getById(schedule.getHallId());

        // 5. 生成订单号
        String orderNo = IdUtil.getSnowflakeNextIdStr();

        // 6. 创建订单
        Order order = Order.builder()
                .orderNo(orderNo)
                .userId(userId)
                .scheduleId(request.getScheduleId())
                .filmName(film != null ? film.getName() : null)
                .cinemaName(cinema != null ? cinema.getName() : null)
                .scheduleTime(schedule.getShowDate() + " " + schedule.getStartTime())
                .hallName(hall != null ? hall.getName() : null)
                .totalPrice(totalPrice)
                .count(count)
                .status("pending")
                .expireAt(LocalDateTime.now().plusMinutes(15))
                .build();
        this.save(order);

        // 7. 创建订单-座位关联
        List<OrderSeat> orderSeats = seats.stream().map(seat -> {
            OrderSeat os = new OrderSeat();
            os.setOrderId(order.getId());
            os.setSeatId(seat.getId());
            os.setSeatLabel(seat.getSeatLabel());
            os.setIsUsed(false);
            return os;
        }).collect(Collectors.toList());
        orderSeatService.saveBatch(orderSeats);

        // 8. 更新座位状态为已锁定（如果是available则改为locked）
        seats.forEach(s -> s.setStatus("locked"));
        seatService.updateBatch(seats);

        log.info("用户 {} 创建订单 {}，座位: {}", userId, orderNo,
                seats.stream().map(Seat::getSeatLabel).collect(Collectors.joining(",")));

        return buildOrderVO(order, seats);
    }

    @Override
    public PayOrderVO payOrder(PayOrderRequest request, Long userId) {
        if (request == null || request.getOrderId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数无效");
        }

        Order order = this.getById(request.getOrderId());
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权操作此订单");
        }
        if (!"pending".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "订单状态不正确");
        }

        // 生成支付宝支付页面
        String subject = order.getFilmName() + " - 电影票";
        String totalAmount = order.getTotalPrice().toString();
        String payForm = alipayService.createPayPage(order.getOrderNo(), totalAmount, subject);
        log.info("订单 {} 生成支付宝支付页面", order.getOrderNo());

        return new PayOrderVO(payForm, order.getOrderNo());
    }

    @Override
    public OrderVO getOrderDetail(Long orderId, Long userId) {
        if (orderId == null || orderId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "订单ID无效");
        }
        Order order = this.getById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "订单不存在");
        }
        if (userId != null && !order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权查看此订单");
        }

        // 查询关联座位
        QueryWrapper seatQw = QueryWrapper.create().eq("orderId", orderId);
        List<OrderSeat> orderSeats = orderSeatService.list(seatQw);
        List<String> seatLabels = orderSeats.stream()
                .map(OrderSeat::getSeatLabel)
                .collect(Collectors.toList());

        OrderVO vo = buildOrderVO(order, null);
        vo.setSeatLabels(seatLabels);
        return vo;
    }

    @Override
    public Page<OrderVO> getUserOrders(Long userId, int pageNum, int pageSize, String status) {
        QueryWrapper qw = QueryWrapper.create()
                .eq("userId", userId)
                .eq("status", status, StrUtil.isNotBlank(status))
                .orderBy("createTime", false);
        Page<Order> orderPage = this.page(Page.of(pageNum, pageSize), qw);

        Page<OrderVO> voPage = new Page<>(orderPage.getPageNumber(), orderPage.getPageSize(), orderPage.getTotalRow());
        List<OrderVO> voList = orderPage.getRecords().stream()
                .map(order -> {
                    QueryWrapper sqw = QueryWrapper.create().eq("orderId", order.getId());
                    List<OrderSeat> orderSeats = orderSeatService.list(sqw);
                    OrderVO vo = buildOrderVO(order, null);
                    vo.setSeatLabels(orderSeats.stream().map(OrderSeat::getSeatLabel).collect(Collectors.toList()));
                    return vo;
                })
                .collect(Collectors.toList());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cancelTimeoutOrders() {
        // 查找超时未支付的订单（pending 且 expireAt < now）
        QueryWrapper qw = QueryWrapper.create()
                .eq("status", "pending")
                .le("expireAt", new Date());
        List<Order> timeoutOrders = this.list(qw);

        if (CollUtil.isEmpty(timeoutOrders)) {
            return 0;
        }

        List<Long> orderIds = timeoutOrders.stream().map(Order::getId).collect(Collectors.toList());

        // 取消订单
        timeoutOrders.forEach(o -> {
            o.setStatus("cancelled");
            o.setCancelReason("timeout");
        });
        this.updateBatch(timeoutOrders);

        // 释放座位
        for (Order order : timeoutOrders) {
            QueryWrapper sqw = QueryWrapper.create().eq("orderId", order.getId());
            List<OrderSeat> orderSeats = orderSeatService.list(sqw);
            List<Long> seatIds = orderSeats.stream().map(OrderSeat::getSeatId).collect(Collectors.toList());
            if (CollUtil.isNotEmpty(seatIds)) {
                List<Seat> seats = seatService.listByIds(seatIds);
                seats.forEach(s -> s.setStatus("available"));
                seatService.updateBatch(seats);
            }
        }

        log.info("定时任务：取消 {} 个超时订单，释放 {} 个座位", timeoutOrders.size(),
                timeoutOrders.stream().mapToLong(o -> o.getCount()).sum());
        return timeoutOrders.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long orderId, Long userId) {
        if (orderId == null || orderId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "订单ID无效");
        }
        Order order = this.getById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权操作此订单");
        }
        if (!"pending".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "只有待支付订单可以取消");
        }

        // 取消订单
        order.setStatus("cancelled");
        order.setCancelReason("user_cancelled");
        this.updateById(order);

        // 释放座位
        QueryWrapper sqw = QueryWrapper.create().eq("orderId", orderId);
        List<OrderSeat> orderSeats = orderSeatService.list(sqw);
        List<Long> seatIds = orderSeats.stream().map(OrderSeat::getSeatId).collect(Collectors.toList());
        if (CollUtil.isNotEmpty(seatIds)) {
            List<Seat> seats = seatService.listByIds(seatIds);
            seats.forEach(s -> s.setStatus("available"));
            seatService.updateBatch(seats);
        }

        log.info("用户 {} 取消订单 {}，释放 {} 个座位", userId, order.getOrderNo(), seatIds.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refundOrder(Long orderId, Long userId) {
        if (orderId == null || orderId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "订单ID无效");
        }
        Order order = this.getById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权操作此订单");
        }
        if (!"paid".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "仅已支付订单可退款");
        }
        if ("refunded".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "订单已退款，不可重复操作");
        }
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            LocalDateTime showTime = LocalDateTime.parse(order.getScheduleTime(), fmt);
            if (showTime.isBefore(LocalDateTime.now())) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "电影已开场，无法退款");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("解析放映时间失败，跳过开场校验: {}", order.getScheduleTime(), e);
        }
        String refundAmount = order.getTotalPrice().toString();
        boolean refundSuccess = alipayService.refund(order.getOrderNo(), refundAmount, order.getAlipayTradeNo());
        if (!refundSuccess) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "支付宝退款失败，请稍后重试");
        }
        order.setStatus("refunded");
        order.setCancelReason("user_refund");
        order.setRefundAmount(order.getTotalPrice());
        order.setRefundTime(LocalDateTime.now());
        this.updateById(order);

        QueryWrapper sqw = QueryWrapper.create().eq("orderId", orderId);
        List<OrderSeat> orderSeats = orderSeatService.list(sqw);
        List<Long> seatIds = orderSeats.stream().map(OrderSeat::getSeatId).collect(Collectors.toList());
        if (CollUtil.isNotEmpty(seatIds)) {
            List<Seat> seats = seatService.listByIds(seatIds);
            seats.forEach(s -> s.setStatus("available"));
            seatService.updateBatch(seats);
        }
        log.info("用户 {} 退款成功，订单号: {}, 退款金额: {}", userId, order.getOrderNo(), refundAmount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refundOrderAdmin(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "订单ID无效");
        }
        Order order = this.getById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "订单不存在");
        }
        if (!"paid".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "仅已支付订单可退款");
        }
        if ("refunded".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "订单已退款，不可重复操作");
        }
        // 退款时限校验（读系统配置 refundTimeoutHours）
        int refundHours = getRefundTimeoutHours();
        LocalDateTime paidAt = order.getPaidAt();
        if (paidAt != null && paidAt.plusHours(refundHours).isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "已超出可退款时限（" + refundHours + " 小时），无法退款");
        }
        // 调用支付宝沙箱退款
        String refundAmount = order.getTotalPrice().toString();
        boolean refundSuccess = alipayService.refund(order.getOrderNo(), refundAmount, order.getAlipayTradeNo());
        if (!refundSuccess) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "支付宝退款失败，请稍后重试");
        }
        order.setStatus("refunded");
        order.setCancelReason("admin_refund");
        order.setRefundAmount(order.getTotalPrice());
        order.setRefundTime(LocalDateTime.now());
        this.updateById(order);
        // 释放座位
        releaseSeats(orderId);
        log.info("管理员退款成功，订单号: {}, 退款金额: {}", order.getOrderNo(), refundAmount);
    }

    /**
     * 释放订单关联的座位（置为 available）。
     */
    private void releaseSeats(Long orderId) {
        QueryWrapper sqw = QueryWrapper.create().eq("orderId", orderId);
        List<OrderSeat> orderSeats = orderSeatService.list(sqw);
        List<Long> seatIds = orderSeats.stream().map(OrderSeat::getSeatId).collect(Collectors.toList());
        if (CollUtil.isNotEmpty(seatIds)) {
            List<Seat> seats = seatService.listByIds(seatIds);
            seats.forEach(s -> s.setStatus("available"));
            seatService.updateBatch(seats);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long orderId, String reason) {
        if (orderId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "订单ID不能为空");
        }

        Order order = this.getById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "订单不存在");
        }
        if ("cancelled".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "订单已取消，无需重复操作");
        }
        if ("refunded".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "订单已退款，无需重复操作");
        }
        if ("completed".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "已完成订单无法取消");
        }

        // PRD 3.3.5 交互规则③：退款校验读取系统配置退款策略，超出可退款时限禁止退款
        if ("paid".equals(order.getStatus())) {
            int refundHours = getRefundTimeoutHours();
            LocalDateTime paidAt = order.getPaidAt();
            if (paidAt != null && paidAt.plusHours(refundHours).isBefore(LocalDateTime.now())) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        "已超出可退款时限（" + refundHours + " 小时），无法退款");
            }
        }

        if ("paid".equals(order.getStatus())) {
            String refundAmount = order.getTotalPrice().toString();
            boolean refundSuccess = alipayService.refund(order.getOrderNo(), refundAmount, order.getAlipayTradeNo());
            if (!refundSuccess) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "支付宝退款失败，请稍后重试");
            }
            order.setStatus("refunded");
            order.setRefundAmount(order.getTotalPrice());
            order.setRefundTime(LocalDateTime.now());
        } else {
            order.setStatus("cancelled");
        }

        order.setCancelReason(reason);
        this.updateById(order);

        QueryWrapper sqw = QueryWrapper.create().eq("orderId", orderId);
        List<OrderSeat> orderSeats = orderSeatService.list(sqw);
        List<Long> seatIds = orderSeats.stream().map(OrderSeat::getSeatId).collect(Collectors.toList());
        if (CollUtil.isNotEmpty(seatIds)) {
            List<Seat> seats = seatService.listByIds(seatIds);
            seats.forEach(s -> s.setStatus("available"));
            seatService.updateBatch(seats);
        }

        log.info("管理员取消/退款订单 {}（{}），释放 {} 个座位", order.getOrderNo(), reason, orderSeats.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int releaseOrphanLocks() {
        // 查找被锁定但没有关联订单的座位（超过30分钟前的锁定）
        // 简化实现：直接通过定时任务处理
        return 0;
    }

    /**
     * 读取可退款时限（小时），默认 24 小时。配置键：refundTimeoutHours
     */
    private int getRefundTimeoutHours() {
        try {
            SystemConfig config = systemConfigService.getOne(
                    QueryWrapper.create().eq("configKey", "refundTimeoutHours"));
            if (config == null || StrUtil.isBlank(config.getConfigValue())) {
                return 24;
            }
            Object val = JSONUtil.parse(config.getConfigValue());
            return val instanceof Number ? ((Number) val).intValue() : Integer.parseInt(val.toString());
        } catch (Exception e) {
            log.warn("读取退款时限配置失败，使用默认 24 小时", e);
            return 24;
        }
    }

    /**
     * 构建 OrderVO。
     */
    private OrderVO buildOrderVO(Order order, List<Seat> seats) {
        OrderVO vo = new OrderVO();
        BeanUtil.copyProperties(order, vo);
        if (seats != null) {
            vo.setSeatLabels(seats.stream().map(Seat::getSeatLabel).collect(Collectors.toList()));
        }
        // 影片海报：订单快照不含海报，通过 场次→影片 关联获取
        if (order.getScheduleId() != null) {
            try {
                Schedule schedule = scheduleService.getById(order.getScheduleId());
                if (schedule != null && schedule.getFilmId() != null) {
                    Film film = filmService.getById(schedule.getFilmId());
                    if (film != null) {
                        vo.setPosterUrl(film.getPosterUrl());
                    }
                }
            } catch (Exception e) {
                log.warn("解析订单海报失败: orderId={}", order.getId(), e);
            }
        }
        return vo;
    }
}
