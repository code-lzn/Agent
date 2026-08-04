package com.limou.agent.controller;

import com.limou.agent.common.BaseResponse;
import com.limou.agent.common.ResultUtils;
import com.mybatisflex.core.paginate.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.limou.agent.model.entity.ChatSession;
import com.limou.agent.service.ChatSessionService;

import java.util.List;

/**
 *  控制层。
 *
 * @author 李振南
 */
@RestController
@RequestMapping("/chatSession")
public class ChatSessionController {

    @Autowired
    private ChatSessionService chatSessionService;

    /**
     * 保存。
     *
     * @param chatSession
     * @return {@code true} 保存成功，{@code false} 保存失败
     */
    @PostMapping("save")
    public BaseResponse<Boolean> save(@RequestBody ChatSession chatSession) {
        return ResultUtils.success(chatSessionService.save(chatSession));
    }

    /**
     * 根据主键删除。
     *
     * @param id 主键
     * @return {@code true} 删除成功，{@code false} 删除失败
     */
    @DeleteMapping("remove/{id}")
    public BaseResponse<Boolean> remove(@PathVariable Long id) {
        return ResultUtils.success(chatSessionService.removeById(id));
    }

    /**
     * 根据主键更新。
     *
     * @param chatSession
     * @return {@code true} 更新成功，{@code false} 更新失败
     */
    @PutMapping("update")
    public BaseResponse<Boolean> update(@RequestBody ChatSession chatSession) {
        return ResultUtils.success(chatSessionService.updateById(chatSession));
    }

    /**
     * 查询所有。
     *
     * @return 所有数据
     */
    @GetMapping("list")
    public List<ChatSession> list() {
        return chatSessionService.list();
    }

    /**
     * 根据主键获取。
     *
     * @param id 主键
     * @return 详情
     */
    @GetMapping("getInfo/{id}")
    public ChatSession getInfo(@PathVariable Long id) {
        return chatSessionService.getById(id);
    }

    /**
     * 分页查询。
     *
     * @param page 分页对象
     * @return 分页对象
     */
    @GetMapping("page")
    public Page<ChatSession> page(Page<ChatSession> page) {
        return chatSessionService.page(page);
    }

    /**
     * 获取或创建当前用户的会话（点击 AI 时调用，有则复用）
     */
    @GetMapping("current")
    public BaseResponse<ChatSession> getCurrentSession(@RequestParam Long userId) {
        return ResultUtils.success(chatSessionService.getOrCreateCurrent(userId));
    }

    /**
     * 强制创建新会话（点击"新对话"按钮时调用）
     */
    @PostMapping("create")
    public BaseResponse<ChatSession> create(@RequestParam Long userId) {
        return ResultUtils.success(chatSessionService.createNew(userId));
    }

    /**
     * 查询用户的所有会话列表（历史记录）
     */
    @GetMapping("listByUser")
    public BaseResponse<java.util.List<ChatSession>> listByUser(@RequestParam Long userId) {
        return ResultUtils.success(chatSessionService.listByUser(userId));
    }

    /**
     * 重命名会话
     */
    @PutMapping("rename")
    public BaseResponse<Boolean> rename(@RequestParam Long id, @RequestParam String name) {
        return ResultUtils.success(chatSessionService.rename(id, name));
    }

}
