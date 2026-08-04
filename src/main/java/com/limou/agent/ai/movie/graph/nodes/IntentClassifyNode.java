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

        // 挂到 graph state 上，后续节点直接透传，不再从 Redis 重复读取
        state.setConvState(convState);

        return state;
    }
}