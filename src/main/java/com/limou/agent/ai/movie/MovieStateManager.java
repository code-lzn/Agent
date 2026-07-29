package com.limou.agent.ai.movie;

import com.limou.agent.model.dto.movie.ConversationState;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 电影票对话状态管理器
 * 使用 Redis 存储和读取每个会话的槽位状态
 * Key: movie:state:{conversationId}
 * TTL: 30 分钟（对话空闲后自动清理）
 */
@Slf4j
@Component
public class MovieStateManager {

    private static final String KEY_PREFIX = "movie:state:";
    private static final Duration STATE_TTL = Duration.ofMinutes(30);

    @Resource
    private RedissonClient redissonClient;

    /**
     * 获取会话状态（不存在则返回初始空状态）
     */
    public ConversationState getState(String conversationId) {
        RBucket<String> bucket = getBucket(conversationId);
        String json = bucket.get();
        if (json != null && !json.isEmpty()) {
            try {
                ConversationState state = ConversationState.fromJson(json);
                state.setConversationId(conversationId);
                log.debug("加载对话状态: conversationId={}, currentStep={}", conversationId, state.getCurrentStep());
                return state;
            } catch (Exception e) {
                log.warn("解析对话状态失败，将使用新状态: conversationId={}", conversationId, e);
            }
        }
        // 返回初始空状态
        ConversationState newState = new ConversationState();
        newState.setConversationId(conversationId);
        newState.setLastUpdate(LocalDateTime.now());
        return newState;
    }

    /**
     * 保存会话状态到 Redis，设置 30 分钟过期
     */
    public void saveState(String conversationId, ConversationState state) {
        state.setConversationId(conversationId);
        state.setLastUpdate(LocalDateTime.now());
        try {
            RBucket<String> bucket = getBucket(conversationId);
            bucket.set(state.toJson(), STATE_TTL);
            log.debug("保存对话状态: conversationId={}, currentStep={}", conversationId, state.getCurrentStep());
        } catch (Exception e) {
            log.error("保存对话状态失败: conversationId={}", conversationId, e);
        }
    }

    /**
     * 合并槽位：将 newState 中的非空字段合并到现有状态中
     */
    public ConversationState mergeState(String conversationId, ConversationState newState) {
        ConversationState existing = getState(conversationId);

        if (newState.getFilmId() != null) existing.setFilmId(newState.getFilmId());
        if (newState.getFilmName() != null) existing.setFilmName(newState.getFilmName());
        if (newState.getFilmType() != null) existing.setFilmType(newState.getFilmType());
        if (newState.getCinemaId() != null) existing.setCinemaId(newState.getCinemaId());
        if (newState.getCinemaName() != null) existing.setCinemaName(newState.getCinemaName());
        if (newState.getLocationTag() != null) existing.setLocationTag(newState.getLocationTag());
        if (newState.getHallType() != null) existing.setHallType(newState.getHallType());
        if (newState.getShowDate() != null) existing.setShowDate(newState.getShowDate());
        if (newState.getStartTime() != null) existing.setStartTime(newState.getStartTime());
        if (newState.getScheduleId() != null) existing.setScheduleId(newState.getScheduleId());
        if (newState.getHallName() != null) existing.setHallName(newState.getHallName());
        if (newState.getTicketCount() != null) existing.setTicketCount(newState.getTicketCount());
        if (newState.getBudgetMax() != null) existing.setBudgetMax(newState.getBudgetMax());
        if (newState.getSeatIds() != null && !newState.getSeatIds().isEmpty()) {
            existing.setSeatIds(newState.getSeatIds());
        }
        if (newState.getSeatLabels() != null && !newState.getSeatLabels().isEmpty()) {
            existing.setSeatLabels(newState.getSeatLabels());
        }
        if (newState.getTotalPrice() != null) existing.setTotalPrice(newState.getTotalPrice());
        if (newState.getOrderId() != null) existing.setOrderId(newState.getOrderId());
        if (newState.getPreferredSeatZone() != null) existing.setPreferredSeatZone(newState.getPreferredSeatZone());
        if (newState.getCurrentStep() != null) existing.setCurrentStep(newState.getCurrentStep());
        if (newState.isPreferencesLoaded()) existing.setPreferencesLoaded(true);
        if (newState.getUserId() != null) existing.setUserId(newState.getUserId());

        saveState(conversationId, existing);
        return existing;
    }

    /**
     * 清除会话状态
     */
    public void clearState(String conversationId) {
        getBucket(conversationId).delete();
        log.debug("清除对话状态: conversationId={}", conversationId);
    }

    /**
     * 生成注入 prompt 的状态上下文文本
     */
    public String generateStatePrompt(String conversationId) {
        ConversationState state = getState(conversationId);
        return state.toPromptContext();
    }

    /**
     * 刷新状态 TTL（延长 30 分钟）
     */
    public void refreshTtl(String conversationId) {
        RBucket<String> bucket = getBucket(conversationId);
        bucket.expire(STATE_TTL);
    }

    private RBucket<String> getBucket(String conversationId) {
        return redissonClient.getBucket(KEY_PREFIX + conversationId);
    }
}
