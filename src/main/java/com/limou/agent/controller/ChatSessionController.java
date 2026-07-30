package com.limou.agent.controller;

import com.limou.agent.common.BaseResponse;
import com.limou.agent.common.ResultUtils;
import com.mybatisflex.core.paginate.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.beans.factory.annotation.Autowired;
import com.limou.agent.model.entity.ChatSession;
import com.limou.agent.service.ChatSessionService;
import org.springframework.web.bind.annotation.RestController;
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

}
