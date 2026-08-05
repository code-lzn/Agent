package com.limou.agent.ai.movie.graph.nodes;

import com.limou.agent.ai.graph.GraphNode;
import com.limou.agent.ai.movie.GuardRailResult;
import com.limou.agent.ai.movie.MovieGuardRail;
import com.limou.agent.ai.movie.graph.MovieGraphState;
import lombok.extern.slf4j.Slf4j;

/**
 * 安全护栏节点
 * <p>
 * 在用户输入进入 LLM 之前进行安全检查，拦截 prompt injection 和异常输入。
 * 如果被拦截，state.blocked 会被设为 true，后续节点应跳过执行。
 */
@Slf4j
public class GuardRailNode implements GraphNode<MovieGraphState> {

    private final MovieGuardRail guardRail;

    public GuardRailNode(MovieGuardRail guardRail) {
        this.guardRail = guardRail;
    }

    @Override
    public MovieGraphState execute(MovieGraphState state) {
        GuardRailResult result = guardRail.check(state.getUserMessage());

        if (!result.allowed()) {
            log.warn("GuardRail 拦截: conversationId={}, reason={}",
                    state.getConversationId(), result.message());
            state.setBlocked(true);
            state.setBlockMessage(result.message());
        }

        return state;
    }
}
