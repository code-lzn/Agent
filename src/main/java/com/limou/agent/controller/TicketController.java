package com.limou.agent.controller;

import cn.hutool.core.util.StrUtil;
import com.limou.agent.annotation.AuthCheck;
import com.limou.agent.common.BaseResponse;
import com.limou.agent.common.ResultUtils;
import com.limou.agent.constant.UserConstant;
import com.limou.agent.exception.ErrorCode;
import com.limou.agent.exception.ThrowUtils;
import com.limou.agent.model.dto.ticket.TicketQueryRequest;
import com.limou.agent.model.entity.User;
import com.limou.agent.model.vo.TicketVO;
import com.limou.agent.service.TicketService;
import com.limou.agent.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 电影票 控制层（票务核销）。
 *
 * @author 李振南
 */
@RestController
@RequestMapping("/ticket")
public class TicketController {

    @Autowired
    private TicketService ticketService;

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
     * 后台 - 核销查询：按取票码查票 + 订单信息（含核销状态）。
     */
    @PostMapping("/admin/query")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<TicketVO> query(@RequestBody TicketQueryRequest request) {
        ThrowUtils.throwIf(request == null || StrUtil.isBlank(request.getTicketCode()),
                ErrorCode.PARAMS_ERROR, "取票码不能为空");
        return ResultUtils.success(ticketService.queryTicket(request.getTicketCode().trim()));
    }

    /**
     * 后台 - 核销单张票（幂等）。
     */
    @PostMapping("/admin/checkin")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<TicketVO> checkin(@RequestBody TicketQueryRequest request,
                                          HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null || StrUtil.isBlank(request.getTicketCode()),
                ErrorCode.PARAMS_ERROR, "取票码不能为空");
        Long operatorId = getLoginUserId(httpRequest);
        return ResultUtils.success(ticketService.checkinTicket(request.getTicketCode().trim(), operatorId));
    }
}
