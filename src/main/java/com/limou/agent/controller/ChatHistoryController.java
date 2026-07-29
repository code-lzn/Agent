package com.limou.agent.controller;

import com.limou.agent.annotation.AuthCheck;
import com.limou.agent.common.BaseResponse;
import com.limou.agent.common.DeleteRequest;
import com.limou.agent.common.ResultUtils;
import com.limou.agent.constant.UserConstant;
import com.limou.agent.exception.BusinessException;
import com.limou.agent.exception.ErrorCode;
import com.limou.agent.exception.ThrowUtils;
import com.limou.agent.model.dto.chathistory.ChatHistoryQueryRequest;
import com.limou.agent.model.entity.ChatHistory;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.limou.agent.service.ChatHistoryService;

import java.util.List;

/**
 * 对话历史 控制层。
 *
 * @author 李振南
 */
@RestController
@RequestMapping("/chatHistory")
public class ChatHistoryController {

    @Resource
    private ChatHistoryService chatHistoryService;

    /**
     * 保存对话历史。
     */
    @PostMapping("save")
    public BaseResponse<Boolean> save(@RequestBody ChatHistory chatHistory) {
        return ResultUtils.success(chatHistoryService.save(chatHistory));
    }

    /**
     * 根据主键删除对话历史。
     */
    @DeleteMapping("remove/{id}")
    public BaseResponse<Boolean> remove(@PathVariable Long id) {
        return ResultUtils.success(chatHistoryService.removeById(id));
    }

    /**
     * 根据主键更新对话历史。
     */
    @PutMapping("update")
    public BaseResponse<Boolean> update(@RequestBody ChatHistory chatHistory) {
        return ResultUtils.success(chatHistoryService.updateById(chatHistory));
    }

    /**
     * 根据主键获取对话历史。
     */
    @GetMapping("getInfo/{id}")
    public BaseResponse<ChatHistory> getInfo(@PathVariable Long id) {
        ChatHistory chatHistory = chatHistoryService.getById(id);
        ThrowUtils.throwIf(chatHistory == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(chatHistory);
    }

    /**
     * 查询所有对话历史。
     */
    @GetMapping("list")
    public BaseResponse<List<ChatHistory>> list() {
        return ResultUtils.success(chatHistoryService.list());
    }

    /**
     * 分页查询对话历史（仅管理员）。
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<ChatHistory>> page(@RequestBody ChatHistoryQueryRequest chatHistoryQueryRequest) {
        ThrowUtils.throwIf(chatHistoryQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = chatHistoryQueryRequest.getPageNum();
        long pageSize = chatHistoryQueryRequest.getPageSize();
        QueryWrapper queryWrapper = chatHistoryService.getQueryWrapper(chatHistoryQueryRequest);
        Page<ChatHistory> result = chatHistoryService.page(Page.of(pageNum, pageSize), queryWrapper);
        return ResultUtils.success(result);
    }

    /**
     * 根据主键删除对话历史（管理员）。
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> delete(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return ResultUtils.success(chatHistoryService.removeById(deleteRequest.getId()));
    }

    /**
     * 根据会话ID查询对话历史。
     */
    @GetMapping("/listBySession/{sessionId}")
    public BaseResponse<List<ChatHistory>> listBySession(@PathVariable Long sessionId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("sessionId", sessionId)
                .orderBy("createTime", true);
        return ResultUtils.success(chatHistoryService.list(queryWrapper));
    }

}
