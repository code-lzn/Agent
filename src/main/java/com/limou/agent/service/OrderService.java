package com.limou.agent.service;

import com.limou.agent.model.dto.order.CreateOrderRequest;
import com.limou.agent.model.dto.order.LockSeatRequest;
import com.limou.agent.model.dto.order.PayOrderRequest;
import com.limou.agent.model.vo.OrderVO;
import com.limou.agent.model.vo.PayOrderVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.service.IService;
import com.limou.agent.model.entity.Order;

import java.util.List;

/**
 * 订单 服务层。
 *
 * @author 李振南
 */
public interface OrderService extends IService<Order> {

    /**
     * 锁定座位（行锁防超卖）。
     *
     * @param request 锁定请求
     * @param userId  用户ID
     * @return 是否锁定成功
     */
    Boolean lockSeat(LockSeatRequest request, Long userId);

    /**
     * 创建订单。
     *
     * @param request 创建请求
     * @param userId  用户ID
     * @return 订单VO
     */
    OrderVO createOrder(CreateOrderRequest request, Long userId);

    /**
     * 支付订单（支付宝沙箱）。
     *
     * @param request 支付请求
     * @param userId  用户ID
     * @return 支付信息（含支付宝表单HTML）
     */
    PayOrderVO payOrder(PayOrderRequest request, Long userId);

    /**
     * 获取订单详情。
     *
     * @param orderId 订单ID
     * @param userId  用户ID
     * @return 订单VO
     */
    OrderVO getOrderDetail(Long orderId, Long userId);

    /**
     * 获取用户订单列表。
     *
     * @param userId   用户ID
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @param status   订单状态（可选）
     * @return 分页结果
     */
    Page<OrderVO> getUserOrders(Long userId, int pageNum, int pageSize, String status);

    /**
     * 取消超时订单（定时任务）。
     */
    int cancelTimeoutOrders();

    /**
     * 用户取消订单。
     *
     * @param orderId 订单ID
     * @param userId  用户ID
     */
    void cancelOrder(Long orderId, Long userId);

    /**
     * 取消指定订单（管理员取消/退款），释放座位。
     *
     * @param orderId 订单ID
     * @param reason  取消原因（timeout / user_cancelled）
     */
    void cancelOrder(Long orderId, String reason);

    /**
     * 用户申请退款（需验证开场时间）。
     */
    void refundOrder(Long orderId, Long userId);

    /**
     * 释放已锁定的座位（无关联订单的锁定座位）。
     */
    int releaseOrphanLocks();

    /**
     * 管理端订单列表：填充「是否有已核销票」标记（用于控制退款入口显示）。
     *
     * @param orders 订单列表（原地修改）
     */
    void fillCheckedStatus(List<Order> orders);
}
