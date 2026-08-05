package com.limou.agent.ai.movie.graph.nodes;

import com.limou.agent.ai.graph.GraphNode;
import com.limou.agent.ai.movie.MovieStateManager;
import com.limou.agent.ai.movie.graph.GraphIntentClassifier;
import com.limou.agent.ai.movie.graph.GraphIntentResult;
import com.limou.agent.ai.movie.graph.MovieGraphState;
import com.limou.agent.model.dto.movie.ConversationState;
import lombok.extern.slf4j.Slf4j;

/**
 * 意图识别节点
 * <p>
 * 调用 LLM 进行意图分类 + 槽位提取。
 * 加载并合并 ConversationState 后挂到 graph state 上，
 * 后续节点直接通过 state.getConvState() 获取，不再各自读 Redis。
 */
@Slf4j
public class IntentClassifyNode implements GraphNode<MovieGraphState> {

    private final GraphIntentClassifier classifier;
    private final MovieStateManager stateManager;

    public IntentClassifyNode(GraphIntentClassifier classifier, MovieStateManager stateManager) {
        this.classifier = classifier;
        this.stateManager = stateManager;
    }

    @Override
    public MovieGraphState execute(MovieGraphState state) {
        if (state.isBlocked()) {
            return state;
        }

        // 从 Redis 加载会话状态（仅此一次）
        ConversationState convState = stateManager.getState(state.getConversationId());
        if (state.getUserId() != null) {
            convState.setUserId(state.getUserId());
        }

        GraphIntentResult intentResult;

        // 优先复用 SmartRouter 的预分类结果
        if (state.getPreclassifiedIntent() != null) {
            intentResult = state.getPreclassifiedIntent();
            log.info("IntentClassify 复用预分类: conversationId={}, intent={}",
                    state.getConversationId(), intentResult.getIntent());
        } else {
            intentResult = classifier.classify(state.getUserMessage(), convState);
            log.info("IntentClassify LLM 分类: conversationId={}, intent={}",
                    state.getConversationId(), intentResult.getIntent());
        }

        // 写入意图（供条件边路由使用）
        state.setIntent(intentResult.getIntent());

        // 合并槽位到 Redis 状态，然后持久化
        if (intentResult.getSlots() != null) {
            convState = stateManager.mergeState(state.getConversationId(), intentResult.getSlots());
        }
        stateManager.saveState(state.getConversationId(), convState);

        // ★ 智能重定向：缺失前置条件时自动降级意图
        String resolvedIntent = resolveIntent(intentResult.getIntent(), convState, state.getConversationId());
        if (!resolvedIntent.equals(intentResult.getIntent())) {
            log.info("IntentClassify 重定向: {} -> {} (conversationId={})",
                    intentResult.getIntent(), resolvedIntent, state.getConversationId());
            state.setIntent(resolvedIntent);
        }

        // 挂到 graph state 上，后续节点直接透传，不再从 Redis 重复读取
        state.setConvState(convState);

        return state;
    }

    /**
     * 智能重定向：当用户意图需要的前置条件不满足时，自动降级到上一步。
     * <pre>
     *   get_seat_map 缺 scheduleId → search_schedule（有 filmId 时）
     *   lock_seats   缺 seatIds   → get_seat_map（有 scheduleId 时）
     *   create_order 缺 seatIds   → lock_seats（有 scheduleId + seatIds in state 时保持 create_order 由 LLM 处理）
     * </pre>
     */
    private String resolveIntent(String intent, ConversationState state, String conversationId) {
        return switch (intent) {
            case "get_seat_map" -> {
                if (state.getScheduleId() == null && state.getFilmId() != null) {
                    yield "search_schedule";
                }
                yield intent;
            }
            case "search_schedule" -> {
                // filmId 未解析但 filmName 已知 → 先搜影片
                if (state.getFilmId() == null && has(state.getFilmName())) {
                    yield "search_movie";
                }
                yield intent;
            }
            case "lock_seats" -> {
                if ((state.getSeatIds() == null || state.getSeatIds().isEmpty())
                        && state.getScheduleId() != null) {
                    yield "get_seat_map";
                }
                yield intent;
            }
            default -> intent;
        };
    }

    private boolean has(String s) {
        return s != null && !s.isEmpty();
    }
}