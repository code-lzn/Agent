package com.limou.agent.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
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
import com.limou.agent.model.vo.SeatLockResult;
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

    @Autowired
    private SeatLockService seatLockService;


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

        // Redis 锁 + 乐观锁（替代 FOR UPDATE 行锁）
        SeatLockResult lockResult = seatLockService.lockSeats(
                request.getScheduleId(), request.getSeatIds(), getLockDuration());
        if (!lockResult.isSuccess()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "锁座失败：" + formatLockError(lockResult));
        }
        log.info("用户 {} 锁定场次 {} 座位: {}", userId, request.getScheduleId(),
                lockResult.getLockedSeats().stream().map(Seat::getSeatLabel).collect(Collectors.joining(",")));
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

        // 1.5 校验电影是否已开场
        if (schedule.getShowDate() != null && schedule.getStartTime() != null) {
            try {
                LocalDateTime showTime = LocalDateTime.parse(
                        schedule.getShowDate() + " " + schedule.getStartTime(),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                if (showTime.isBefore(LocalDateTime.now())) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "电影已开场，无法购票");
                }
            } catch (BusinessException e) { throw e; }
            catch (Exception ignored) { /* 解析失败跳过 */ }
        }

        // 2. 锁座（Redis 锁 + 乐观锁，替代 FOR UPDATE 行锁）
        SeatLockResult lockResult = seatLockService.lockSeats(
                request.getScheduleId(), request.getSeatIds(), getLockDuration());
        if (!lockResult.isSuccess()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "座位已被占用：" + formatLockError(lockResult));
        }
        List<Seat> seats = lockResult.getLockedSeats();

        try {
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
                    .expireAt(LocalDateTime.now().plusMinutes(getLockDuration()))
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
            // 座位状态已由 lockSeats 置为 locked，无需重复更新

            log.info("用户 {} 创建订单 {}，座位: {}", userId, orderNo,
                    seats.stream().map(Seat::getSeatLabel).collect(Collectors.joining(",")));

            return buildOrderVO(order, seats);
        } catch (Exception e) {
            // 后续步骤失败 → 释放 Redis 锁（座位状态由事务回滚）
            seatLockService.releaseSeats(request.getScheduleId(), request.getSeatIds());
            throw e;
        }
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
    @Transactional(rollbackFor = Exception.class)
    public OrderVO mockPayOrder(PayOrderRequest request, Long userId) {
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
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "仅待支付订单可模拟支付");
        }

        // 模拟支付：直接标记为已支付
        order.setStatus("paid");
        order.setPaidAt(LocalDateTime.now());
        order.setAlipayTradeNo("MOCK_" + IdUtil.getSnowflakeNextIdStr());
        order.setAlipayStatus("TRADE_SUCCESS");
        this.updateById(order);

        // 更新座位为已售
        QueryWrapper sqw = QueryWrapper.create().eq("orderId", order.getId());
        List<OrderSeat> orderSeats = orderSeatService.list(sqw);
        List<Long> seatIds = orderSeats.stream().map(OrderSeat::getSeatId).collect(Collectors.toList());
        if (CollUtil.isNotEmpty(seatIds)) {
            List<Seat> seats = seatService.listByIds(seatIds);
            seats.forEach(s -> s.setStatus("sold"));
            seatService.updateBatch(seats);
        }

        log.info("用户 {} 模拟支付成功，订单号: {}", userId, order.getOrderNo());

        List<String> seatLabels = orderSeats.stream()
                .map(OrderSeat::getSeatLabel)
                .collect(Collectors.toList());
        OrderVO vo = buildOrderVO(order, null);
        vo.setSeatLabels(seatLabels);
        return vo;
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
        List<Order> orders = orderPage.getRecords();

        Page<OrderVO> voPage = new Page<>(orderPage.getPageNumber(), orderPage.getPageSize(), orderPage.getTotalRow());
        if (CollUtil.isEmpty(orders)) {
            voPage.setRecords(Collections.emptyList());
            return voPage;
        }

        // 批量查座位（一次查询替代 N 次）
        List<Long> orderIds = orders.stream().map(Order::getId).collect(Collectors.toList());
        QueryWrapper seatQw = QueryWrapper.create().in("orderId", orderIds);
        List<OrderSeat> allSeats = orderSeatService.list(seatQw);
        Map<Long, List<String>> seatLabelMap = allSeats.stream()
                .collect(Collectors.groupingBy(OrderSeat::getOrderId,
                        Collectors.mapping(OrderSeat::getSeatLabel, Collectors.toList())));

        // 批量查排期→影片拿海报（一次查排期 + 一次查影片，替代 N*2 次）
        List<Long> scheduleIds = orders.stream().map(Order::getScheduleId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        final Map<Long, Schedule> scheduleMap;
        final Map<Long, Film> filmMap;
        if (CollUtil.isNotEmpty(scheduleIds)) {
            List<Schedule> schedules = scheduleService.listByIds(scheduleIds);
            scheduleMap = schedules.stream().collect(Collectors.toMap(Schedule::getId, s -> s));
            List<Long> filmIds = schedules.stream().map(Schedule::getFilmId)
                    .filter(Objects::nonNull).distinct().collect(Collectors.toList());
            if (CollUtil.isNotEmpty(filmIds)) {
                List<Film> films = filmService.listByIds(filmIds);
                filmMap = films.stream().collect(Collectors.toMap(Film::getId, f -> f));
            } else {
                filmMap = Collections.emptyMap();
            }
        } else {
            scheduleMap = Collections.emptyMap();
            filmMap = Collections.emptyMap();
        }

        // 内存组装 VO
        List<OrderVO> voList = orders.stream().map(order -> {
            OrderVO vo = new OrderVO();
            BeanUtil.copyProperties(order, vo);
            vo.setSeatLabels(seatLabelMap.getOrDefault(order.getId(), Collections.emptyList()));
            Schedule schedule = scheduleMap.get(order.getScheduleId());
            if (schedule != null) {
                Film film = filmMap.get(schedule.getFilmId());
                if (film != null) vo.setPosterUrl(film.getPosterUrl());
            }
            return vo;
        }).collect(Collectors.toList());

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
                // 释放座位 + Redis 锁（统一走锁服务）
                seatLockService.releaseSeatsToAvailable(order.getScheduleId(), seatIds);
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
            // 释放座位 + Redis 锁（统一走锁服务）
            seatLockService.releaseSeatsToAvailable(order.getScheduleId(), seatIds);
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
        if ("expired".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "电影已结束，无法退票");
        }

        // 开场前1分钟内不可退票
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            LocalDateTime showTime = LocalDateTime.parse(order.getScheduleTime(), fmt);
            long diffMinutes = java.time.Duration.between(LocalDateTime.now(), showTime).toMinutes();
            if (diffMinutes < 1) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "开场前1分钟内不支持退票");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("解析放映时间失败: {}", order.getScheduleTime(), e);
        }

        BigDecimal refundAmount = order.getTotalPrice();
        int count = order.getCount() != null ? order.getCount() : 1;

        String tradeNo = order.getAlipayTradeNo();
        if (tradeNo == null || tradeNo.startsWith("MOCK_")) {
            order.setStatus("refunded");
            order.setCancelReason("退票");
            order.setRefundAmount(refundAmount);
            order.setRefundTime(LocalDateTime.now());
            this.updateById(order);
        } else {
            boolean refundSuccess = alipayService.refund(order.getOrderNo(), refundAmount.toString(), tradeNo);
            if (!refundSuccess) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "支付宝退款失败，请稍后重试");
            }
            order.setStatus("refunded");
            order.setCancelReason("退票");
            order.setRefundAmount(refundAmount);
            order.setRefundTime(LocalDateTime.now());
            this.updateById(order);
        }

        QueryWrapper sqw = QueryWrapper.create().eq("orderId", orderId);
        List<OrderSeat> orderSeats = orderSeatService.list(sqw);
        List<Long> seatIds = orderSeats.stream().map(OrderSeat::getSeatId).collect(Collectors.toList());
        if (CollUtil.isNotEmpty(seatIds)) {
            seatLockService.releaseSeatsToAvailable(order.getScheduleId(), seatIds);
        }
        log.info("用户 {} 退票成功，订单号: {}, {}张, 退款{}元",
                userId, order.getOrderNo(), count, refundAmount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrder(Long orderId, Long userId) {
        Order order = this.getById(orderId);
        if (order == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "订单不存在");
        if (!order.getUserId().equals(userId)) throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        if ("pending".equals(order.getStatus()) || "paid".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "仅已退款、已取消或已过期的订单可删除");
        }
        this.removeById(orderId);
        log.info("用户 {} 删除订单 {}", userId, order.getOrderNo());
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
        if ("expired".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "电影已结束，无法操作");
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
            // 释放座位 + Redis 锁（统一走锁服务）
            seatLockService.releaseSeatsToAvailable(order.getScheduleId(), seatIds);
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
        return readIntConfig("refundTimeoutHours", 24);
    }

    /**
     * 读取锁座时长（分钟），即待支付订单超时时间，默认 15 分钟。配置键：lockDuration
     */
    private int getLockDuration() {
        return readIntConfig("lockDuration", 15);
    }

    /**
     * 读取整型系统配置。配置值可能是裸数字（如 20）或带引号的 JSON 数字，统一容错解析。
     */
    private int readIntConfig(String configKey, int defaultValue) {
        try {
            SystemConfig config = systemConfigService.getOne(
                    QueryWrapper.create().eq("configKey", configKey));
            if (config == null || StrUtil.isBlank(config.getConfigValue())) {
                return defaultValue;
            }
            String v = config.getConfigValue().trim().replaceAll("[\"']", "");
            return Integer.parseInt(v);
        } catch (Exception e) {
            log.warn("读取配置 {} 失败，使用默认 {}", configKey, defaultValue, e);
            return defaultValue;
        }
    }

    /**
     * 格式化锁座失败提示（拼接冲突座位标签）。
     */
    private String formatLockError(SeatLockResult result) {
        List<String> labels = result.getConflictSeatLabels();
        if (labels == null || labels.isEmpty()) {
            return "座位已被占用，请重新选座";
        }
        return String.join("；", labels);
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
                if (schedule != null) {
                    if (schedule.getFilmId() != null) {
                        Film film = filmService.getById(schedule.getFilmId());
                        if (film != null) {
                            vo.setPosterUrl(film.getPosterUrl());
                        }
                    }
                    // 影院标签：通过 场次→影院 获取
                    if (schedule.getCinemaId() != null) {
                        Cinema cinema = cinemaService.getById(schedule.getCinemaId());
                        if (cinema != null) {
                            vo.setCinemaTags(cinema.getTags());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("解析订单海报失败: orderId={}", order.getId(), e);
            }
        }
        return vo;
    }
}
