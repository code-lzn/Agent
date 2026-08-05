package com.limou.agent.service;

import com.limou.agent.model.entity.Seat;
import com.limou.agent.model.entity.Ticket;
import com.limou.agent.model.vo.TicketVO;
import com.mybatisflex.core.service.IService;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 电影票 服务层。
 *
 * @author 李振南
 */
public interface TicketService extends IService<Ticket> {

    /**
     * 下单时为每个座位生成一张票（独立 8 位取票码，唯一）。
     *
     * @param orderId    订单ID
     * @param scheduleId 场次ID
     * @param seats      已锁定的座位
     * @return 生成的票列表
     */
    List<Ticket> createTickets(Long orderId, Long scheduleId, List<Seat> seats);

    /**
     * 按取票码查询票（不存在返回 null）。
     */
    Ticket getByTicketCode(String ticketCode);

    /**
     * 核销查询：按取票码查票 + 订单信息（含核销状态），不执行核销。
     */
    TicketVO queryTicket(String ticketCode);

    /**
     * 核销单张票。
     *
     * @param ticketCode 取票码
     * @param operatorId 核销人（后台管理员用户ID）
     * @return 核销后的票信息
     */
    TicketVO checkinTicket(String ticketCode, Long operatorId);

    /**
     * 订单是否存在已核销的票（用于退款/取消拦截）。
     */
    boolean hasUsedTicket(Long orderId);

    /**
     * 订单的全部票（按座位）。
     */
    List<Ticket> listByOrder(Long orderId);

    /**
     * 订单退款成功后，将其未核销的票批量置为「已退票」。
     *
     * @return 更新数量
     */
    int markRefunded(Long orderId);

    /**
     * 订单的全部票（含动态核销状态，用于订单详情展示）。
     */
    List<TicketVO> getTicketsByOrder(Long orderId);

    /**
     * 批量查询订单集合中「有已核销票」的订单ID（管理端列表填充用）。
     */
    Set<Long> getCheckedOrderIds(Collection<Long> orderIds);

    /**
     * 批量查订单的票（按 orderId 分组，含核销状态，用于列表一次查询）。
     */
    Map<Long, List<TicketVO>> getTicketsMapByOrderIds(Collection<Long> orderIds);
}
