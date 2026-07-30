package com.limou.agent.controller;

import com.limou.agent.annotation.AuthCheck;
import com.limou.agent.common.BaseResponse;
import com.limou.agent.common.ResultUtils;
import com.limou.agent.constant.UserConstant;
import com.limou.agent.exception.ErrorCode;
import com.limou.agent.exception.ThrowUtils;
import com.limou.agent.model.dto.order.CreateOrderRequest;
import com.limou.agent.model.dto.order.LockSeatRequest;
import com.limou.agent.model.dto.order.PayOrderRequest;
import com.limou.agent.model.entity.Order;
import com.limou.agent.model.entity.User;
import com.limou.agent.model.vo.OrderVO;
import com.limou.agent.model.vo.PayOrderVO;
import com.limou.agent.service.OrderService;
import com.limou.agent.service.UserService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 订单 控制层。
 *
 * @author 李振南
 */
@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Resource
    private UserService userService;

    /**
     * 获取当前登录用户ID。
     */
    private Long getLoginUserId(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return loginUser.getId();
    }

    /**
     * 锁定座位（行锁防超卖）。
     */
    @PostMapping("/lockSeat")
    public BaseResponse<Boolean> lockSeat(@RequestBody LockSeatRequest request, HttpServletRequest httpRequest) {
        Long userId = getLoginUserId(httpRequest);
        Boolean result = orderService.lockSeat(request, userId);
        return ResultUtils.success(result);
    }

    /**
     * 创建订单。
     */
    @PostMapping("/create")
    public BaseResponse<OrderVO> createOrder(@RequestBody CreateOrderRequest request, HttpServletRequest httpRequest) {
        Long userId = getLoginUserId(httpRequest);
        OrderVO orderVO = orderService.createOrder(request, userId);
        return ResultUtils.success(orderVO);
    }

    /**
     * 支付订单（支付宝沙箱）—— 返回支付宝支付页面HTML表单。
     */
    @PostMapping("/pay")
    public BaseResponse<PayOrderVO> payOrder(@RequestBody PayOrderRequest request, HttpServletRequest httpRequest) {
        Long userId = getLoginUserId(httpRequest);
        PayOrderVO payOrderVO = orderService.payOrder(request, userId);
        return ResultUtils.success(payOrderVO);
    }

    /**
     * 订单详情。
     */
    @GetMapping("/{id}")
    public BaseResponse<OrderVO> getOrderDetail(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = getLoginUserId(httpRequest);
        OrderVO orderVO = orderService.getOrderDetail(id, userId);
        return ResultUtils.success(orderVO);
    }

    /**
     * 订单列表（当前用户）。
     */
    @GetMapping("/list")
    public BaseResponse<Page<OrderVO>> listOrders(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpServletRequest httpRequest) {
        Long userId = getLoginUserId(httpRequest);
        Page<OrderVO> orderPage = orderService.getUserOrders(userId, pageNum, pageSize);
        return ResultUtils.success(orderPage);
    }

    // ========== 后台管理接口 ==========

    /**
     * 后台 - 订单列表（所有订单）。
     */
    @GetMapping("/admin/list")
    public BaseResponse<Page<Order>> adminList(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status) {
        QueryWrapper qw = QueryWrapper.create()
                .eq("status", status, status != null)
                .orderBy("createTime", false);
        Page<Order> orderPage = orderService.page(Page.of(pageNum, pageSize), qw);
        return ResultUtils.success(orderPage);
    }

    /**
     * 后台 - 取消/退款订单。
     */
    @PostMapping("/admin/cancel/{id}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> adminCancel(@PathVariable Long id) {
        orderService.cancelTimeoutOrders(); // 复用取消逻辑
        return ResultUtils.success(true);
    }
}
