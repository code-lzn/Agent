package com.limou.agent.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.limou.agent.exception.BusinessException;
import com.limou.agent.exception.ErrorCode;
import com.limou.agent.mapper.TicketMapper;
import com.limou.agent.model.entity.Order;
import com.limou.agent.model.entity.Schedule;
import com.limou.agent.model.entity.Seat;
import com.limou.agent.model.entity.Ticket;
import com.limou.agent.model.enums.OrderStatusEnum;
import com.limou.agent.model.enums.TicketStatusEnum;
import com.limou.agent.model.vo.TicketVO;
import com.limou.agent.service.OrderService;
import com.limou.agent.service.ScheduleService;
import com.limou.agent.service.TicketService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 电影票 服务层实现。
 *
 * @author 李振南
 */
@Service
@Slf4j
public class TicketServiceImpl extends ServiceImpl<TicketMapper, Ticket> implements TicketService {

    private static final Random RANDOM = new Random();

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    @Lazy
    private OrderService orderService;

    /** 生成 8 位数字取票码（前导 0 补位） */
    private String generateTicketCode() {
        return String.format("%08d", RANDOM.nextInt(100000000));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Ticket> createTickets(Long orderId, Long scheduleId, List<Seat> seats) {
        if (orderId == null || scheduleId == null || CollUtil.isEmpty(seats)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数无效");
        }
        // 现有取票码集合（唯一索引 uk_ticketCode 兜底，这里先查重避免批量冲突）
        Set<String> existing = mapper.selectListByQuery(QueryWrapper.create().select("ticketCode"))
                .stream().map(Ticket::getTicketCode).collect(Collectors.toSet());

        List<Ticket> tickets = new ArrayList<>();
        for (Seat seat : seats) {
            String code;
            do {
                code = generateTicketCode();
            } while (existing.contains(code));
            existing.add(code);

            Ticket t = new Ticket();
            t.setOrderId(orderId);
            t.setScheduleId(scheduleId);
            t.setSeatId(seat.getId());
            t.setSeatLabel(seat.getSeatLabel());
            t.setTicketCode(code);
            t.setStatus(TicketStatusEnum.UNUSED.getValue());
            tickets.add(t);
        }
        boolean saved = saveBatch(tickets);
        if (!saved) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "票生成失败");
        }
        log.info("订单 {} 生成 {} 张票", orderId, tickets.size());
        return tickets;
    }

    @Override
    public Ticket getByTicketCode(String ticketCode) {
        if (ticketCode == null || ticketCode.isBlank()) {
            return null;
        }
        return getOne(QueryWrapper.create().eq("ticketCode", ticketCode.trim()));
    }

    @Override
    public TicketVO queryTicket(String ticketCode) {
        Ticket ticket = getByTicketCode(ticketCode);
        if (ticket == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "票不存在或取票码有误");
        }
        return buildTicketVO(ticket);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketVO checkinTicket(String ticketCode, Long operatorId) {
        Ticket ticket = getByTicketCode(ticketCode);
        if (ticket == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "票不存在或取票码有误");
        }
        // 幂等 + 状态校验：已核销/已退票/已过期 均不可再核销
        Integer st = ticket.getStatus();
        if (st != null && TicketStatusEnum.CHECKED == TicketStatusEnum.getEnumByValue(st)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "该票已于 " + (ticket.getCheckedInAt() != null ? ticket.getCheckedInAt() : "") + " 核销");
        }
        if (st != null && TicketStatusEnum.REFUNDED == TicketStatusEnum.getEnumByValue(st)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该票已退票，无法核销");
        }
        // 已过期（落库 status=3，由定时任务写入；未落库时走下方 isExpired 动态判定）
        if (st != null && TicketStatusEnum.EXPIRED == TicketStatusEnum.getEnumByValue(st)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该票已过期，无法核销");
        }
        if (isExpired(ticket)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该票已过期，无法核销");
        }

        // 校验订单状态：仅已支付订单可核销
        Order order = orderService.getById(ticket.getOrderId());
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "订单不存在");
        }
        if (!OrderStatusEnum.PAID.getValue().equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "订单未支付或已取消/退款，无法核销");
        }

        // 核销
        ticket.setStatus(1);
        ticket.setCheckedInAt(LocalDateTime.now());
        ticket.setCheckedBy(operatorId);
        boolean updated = updateById(ticket);
        if (!updated) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "核销失败，请重试");
        }
        log.info("管理员 {} 核销票 {}（订单 {} 座位 {}）", operatorId, ticket.getTicketCode(),
                ticket.getOrderId(), ticket.getSeatLabel());
        return buildTicketVO(ticket);
    }

    @Override
    public boolean hasUsedTicket(Long orderId) {
        if (orderId == null) {
            return false;
        }
        return count(QueryWrapper.create().eq("orderId", orderId).eq("status", 1)) > 0;
    }

    @Override
    public List<Ticket> listByOrder(Long orderId) {
        if (orderId == null) {
            return new ArrayList<>();
        }
        return list(QueryWrapper.create().eq("orderId", orderId));
    }

    @Override
    public Set<Long> getCheckedOrderIds(Collection<Long> orderIds) {
        if (CollUtil.isEmpty(orderIds)) {
            return new HashSet<>();
        }
        return mapper.selectListByQuery(QueryWrapper.create()
                        .in("orderId", orderIds)
                        .eq("status", TicketStatusEnum.CHECKED.getValue())
                        .select("orderId"))
                .stream().map(Ticket::getOrderId).collect(Collectors.toSet());
    }

    @Override
    public Map<Long, List<TicketVO>> getTicketsMapByOrderIds(Collection<Long> orderIds) {
        if (CollUtil.isEmpty(orderIds)) {
            return new HashMap<>();
        }
        List<Ticket> tickets = list(QueryWrapper.create().in("orderId", orderIds));
        if (CollUtil.isEmpty(tickets)) {
            return new HashMap<>();
        }
        return tickets.stream().collect(Collectors.groupingBy(Ticket::getOrderId,
                Collectors.mapping(t -> buildTicketVO(t, null), Collectors.toList())));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markRefunded(Long orderId) {
        if (orderId == null) {
            return 0;
        }
        List<Ticket> tickets = listByOrder(orderId);
        if (CollUtil.isEmpty(tickets)) {
            return 0;
        }
        int n = 0;
        for (Ticket t : tickets) {
            // 仅未核销的票置为已退票（已核销/已退票的不动）
            if (t.getStatus() == null || TicketStatusEnum.UNUSED == TicketStatusEnum.getEnumByValue(t.getStatus())) {
                t.setStatus(TicketStatusEnum.REFUNDED.getValue());
                updateById(t);
                n++;
            }
        }
        return n;
    }

    /**
     * 票是否已过期：未核销且场次已结束。
     */
    private boolean isExpired(Ticket ticket) {
        if (ticket.getStatus() != null && TicketStatusEnum.UNUSED != TicketStatusEnum.getEnumByValue(ticket.getStatus())) {
            return false;
        }
        Schedule schedule = scheduleService.getById(ticket.getScheduleId());
        if (schedule == null || schedule.getShowDate() == null || schedule.getEndTime() == null) {
            return false;
        }
        try {
            LocalDateTime endTime = LocalDateTime.of(schedule.getShowDate().toLocalDate(),
                    LocalTime.parse(schedule.getEndTime()));
            return LocalDateTime.now().isAfter(endTime);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 展示状态：存储值(0/1/2) + 动态判定「已过期(3)」。
     * 已过期不落库，查询/核销时实时计算，避免依赖定时任务。
     */
    private Integer resolveStatus(Ticket ticket) {
        if (ticket.getStatus() != null && TicketStatusEnum.UNUSED != TicketStatusEnum.getEnumByValue(ticket.getStatus())) {
            return ticket.getStatus();
        }
        return isExpired(ticket)
                ? TicketStatusEnum.EXPIRED.getValue()
                : TicketStatusEnum.UNUSED.getValue();
    }

    @Override
    public List<TicketVO> getTicketsByOrder(Long orderId) {
        List<Ticket> tickets = listByOrder(orderId);
        if (CollUtil.isEmpty(tickets)) {
            return new ArrayList<>();
        }
        Order order = orderService.getById(orderId);
        return tickets.stream().map(t -> buildTicketVO(t, order)).collect(Collectors.toList());
    }

    /** 组装票 + 订单冗余信息（单票查询用） */
    private TicketVO buildTicketVO(Ticket ticket) {
        return buildTicketVO(ticket, orderService.getById(ticket.getOrderId()));
    }

    /** 组装票 + 订单冗余信息 */
    private TicketVO buildTicketVO(Ticket ticket, Order order) {
        TicketVO vo = new TicketVO();
        vo.setId(ticket.getId());
        vo.setOrderId(ticket.getOrderId());
        vo.setScheduleId(ticket.getScheduleId());
        vo.setSeatId(ticket.getSeatId());
        vo.setSeatLabel(ticket.getSeatLabel());
        vo.setTicketCode(ticket.getTicketCode());
        vo.setStatus(resolveStatus(ticket));
        vo.setCheckedInAt(ticket.getCheckedInAt());
        vo.setCheckedBy(ticket.getCheckedBy());

        if (order != null) {
            vo.setOrderNo(order.getOrderNo());
            vo.setOrderStatus(order.getStatus());
            vo.setFilmName(order.getFilmName());
            vo.setCinemaName(order.getCinemaName());
            vo.setHallName(order.getHallName());
            vo.setScheduleTime(order.getScheduleTime());
        }
        return vo;
    }
}
