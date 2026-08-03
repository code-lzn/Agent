package com.limou.agent.ai.movie.graph;

import com.limou.agent.ai.movie.*;
import com.limou.agent.ai.movie.tools.*;
import com.limou.agent.model.dto.movie.ConversationState;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 电影票 Agent StateGraph 工作流
 *
 * 架构: 代码控流程 + LLM 做意图/回复
 * 流程: GuardRail → 意图识别 → 工具路由 → 工具执行 → 回复生成
 *
 * 与 ReAct 模式的区别: LLM 不持有工具，由 Graph 根据意图精确路由到具体工具
 */
@Slf4j
@Component
public class MovieGraphWorkflow {

    @Resource
    private MovieGuardRail movieGuardRail;

    @Resource
    private MovieStateManager movieStateManager;

    @Resource
    private GraphIntentClassifier graphIntentClassifier;

    // ===== 工具实例 =====
    @Resource
    private SearchFilmsTool searchFilmsTool;

    @Resource
    private SearchCinemasTool searchCinemasTool;

    @Resource
    private SearchSchedulesTool searchSchedulesTool;

    @Resource
    private GetSeatMapTool getSeatMapTool;

    @Resource
    private LockSeatsTool lockSeatsTool;

    @Resource
    private CreateOrderTool createOrderTool;

    @Resource
    private PayOrderTool payOrderTool;

    @Resource
    private GetUserPreferenceTool getUserPreferenceTool;

    /**
     * 执行 Graph 工作流
     * 1. GuardRail 安全检查
     * 2. LLM 意图识别 + 槽位提取
     * 3. 代码根据意图精确路由到工具
     * 4. LLM 生成自然语言回复
     *
     * @param message        用户输入
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @return 工作流决策（含回复文本）
     */
    public WorkflowDecision execute(String message, String conversationId, Long userId) {
        log.info("GraphWorkflow 开始: conversationId={}", conversationId);

        // === 1. GuardRail 安全检查 ===
        GuardRailResult guardResult = movieGuardRail.check(message);
        if (!guardResult.allowed()) {
            log.warn("Graph GuardRail 拦截: {}", guardResult.message());
            return WorkflowDecision.blocked(guardResult.message());
        }

        // === 2. 加载会话状态 ===
        ConversationState state = movieStateManager.getState(conversationId);
        if (userId != null) {
            state.setUserId(userId);
        }

        // === 3. LLM 意图识别 + 槽位提取 ===
        GraphIntentResult intentResult = graphIntentClassifier.classify(message, state);
        String intent = intentResult.getIntent();

        // 合并槽位
        if (intentResult.getSlots() != null) {
            state = movieStateManager.mergeState(conversationId, intentResult.getSlots());
        }
        movieStateManager.saveState(conversationId, state);

        // === 4. 工具路由 + 执行 ===
        String toolResult = "";
        String toolName = intent;

        if (isToolIntent(intent)) {
            try {
                toolResult = executeTool(intent, state);
                log.info("Graph 工具执行完成: intent={}", intent);
            } catch (Exception e) {
                log.error("Graph 工具执行失败: intent={}", intent, e);
                toolResult = "{\"error\":\"" + e.getMessage() + "\"}";
            }
        }

        log.info("GraphWorkflow 完成: intent={}, hasToolResult={}", intent,
                !toolResult.isEmpty());

        // 回复生成移到上层，由流式调用完成，避免双重 LLM 调用
        return WorkflowDecision.builder()
                .blocked(false)
                .intent(intent)
                .toolResult(toolResult)
                .toolName(toolName)
                .stateJson(state.toJson())
                .build();
    }

    /**
     * 判断意图是否需要工具调用
     */
    private boolean isToolIntent(String intent) {
        return switch (intent) {
            case "search_movie", "search_cinema", "search_schedule",
                    "get_seat_map", "lock_seats", "create_order",
                    "pay_order", "get_preference" ->
                true;
            default -> false;
        };
    }

    /**
     * 根据意图精确调用对应工具
     * 代码路由，不依赖 LLM 选择工具
     */
    private String executeTool(String intent, ConversationState state) {
        return switch (intent) {
            case "search_movie" -> searchFilmsTool.searchFilms(
                    state.getFilmName(), state.getFilmType(), "rating_desc");

            case "search_cinema" -> searchCinemasTool.searchCinemas(
                    state.getCinemaName(), null, state.getFilmId());

            case "search_schedule" -> searchSchedulesTool.searchSchedules(
                    state.getFilmId(), state.getCinemaId(),
                    state.getShowDate(), state.getHallType());

            case "get_seat_map" -> getSeatMapTool.getSeatMap(
                    state.getScheduleId());

            case "lock_seats" -> lockSeatsTool.lockSeats(
                    state.getScheduleId(), state.getSeatIds());

            case "create_order" -> createOrderTool.createOrder(
                    state.getScheduleId(), state.getSeatIds(), state.getUserId());

            case "pay_order" -> payOrderTool.payOrder(
                    state.getOrderId(), "wechat");

            case "get_preference" -> getUserPreferenceTool.getUserPreference(
                    state.getUserId());

            default -> "{\"error\":\"未知意图: " + intent + "\"}";
        };
    }
}