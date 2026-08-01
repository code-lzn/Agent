package com.limou.agent.ai.movie.graph;

import com.limou.agent.model.dto.movie.ConversationState;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 智能路由器
 * 混合策略（规则 + LLM）判断用户消息应该走 ReAct 还是 Graph
 *
 * ReAct: 用户一句话提供足够信息 → LLM 自主链式调工具，一次完成
 * Graph: 用户信息不足/闲聊 → 代码控流程逐步引导
 */
@Slf4j
@Component
public class SmartMovieRouter {

    /** 一条消息里同时出现"订/买/购" + 以下至少 2 类关键词 → 直接 ReAct */
    private static final Pattern BOOKING_KEYWORD = Pattern.compile("(订|买|购|下单|抢).*(票|座|位)");
    private static final Pattern MOVIE_NAME_HINT = Pattern.compile("《.+》|流浪地球|封神|哪吒|长安|万里|满江红|热辣|飞驰|人生|八角笼|孤注一掷|消失的她");
    private static final Pattern TIME_HINT = Pattern.compile("(今天|明天|后天|周[一二三四五六日]|上午|下午|晚上|凌晨|\\d+点|\\d+:\\d+)");
    private static final Pattern CINEMA_HINT = Pattern.compile("(影院|影城|万达|CGV|金逸|中影|大地|博纳|卢米埃|百老汇|英皇|UA|离.{0,5}近|附近|旁边)");
    private static final Pattern GREETING_PATTERN = Pattern.compile("^(你好|hi|hello|嗨|在吗|在不在|嘿|哈喽|早上好|下午好|晚上好)[!！。.]*$");
    private static final Pattern CANCEL_PATTERN = Pattern.compile("(算了|不买了|取消|不要了|放弃|改天|下次)");

    @Resource
    private GraphIntentClassifier intentClassifier;

    /**
     * 路由决策
     *
     * @param message 用户输入
     * @param state   当前会话状态（含历史槽位）
     * @return REACT 或 GRAPH
     */
    public RouterDecision route(String message, ConversationState state) {
        // === 规则层（零延迟） ===

        // 纯问候 → Graph
        if (matches(GREETING_PATTERN, message)) {
            log.info("Router: 规则命中 问候 → GRAPH");
            return RouterDecision.GRAPH;
        }

        // 取消/放弃 → Graph
        if (matches(CANCEL_PATTERN, message)) {
            log.info("Router: 规则命中 取消 → GRAPH");
            return RouterDecision.GRAPH;
        }

        // 一条消息包含 订票动作 + 影片名 + (时间 或 影院) → ReAct
        if (matches(BOOKING_KEYWORD, message)
                && matches(MOVIE_NAME_HINT, message)
                && (matches(TIME_HINT, message) || matches(CINEMA_HINT, message))) {
            log.info("Router: 规则命中 一句话订票 → REACT");
            return RouterDecision.REACT;
        }

        // === LLM 层（规则拿不准时） ===
        try {
            GraphIntentResult result = intentClassifier.classify(message, state);
            int filledSlots = countFilledSlots(result.getSlots(), state);

            // 合并历史状态中的槽位
            int totalFilled = countMergedSlots(result.getSlots(), state);

            // ≥4 个槽位 → 用户意图明确，给 LLM 全量工具一次性跑完
            if (totalFilled >= 4) {
                log.info("Router: LLM 判定 槽位{}/7 ≥4 → REACT", totalFilled);
                return RouterDecision.REACT;
            }

            log.info("Router: LLM 判定 槽位{}/7 <4 → GRAPH", totalFilled);
            return RouterDecision.GRAPH;

        } catch (Exception e) {
            log.warn("Router: LLM 判定异常，兜底走 Graph", e);
            return RouterDecision.GRAPH;
        }
    }

    /**
     * 统计槽位填充数（仅当前消息提取的）
     */
    private int countFilledSlots(ConversationState slots, ConversationState state) {
        int count = 0;
        if (has(slots.getFilmName())) count++;
        if (has(slots.getFilmType())) count++;
        if (has(slots.getCinemaName())) count++;
        if (has(slots.getHallType())) count++;
        if (has(slots.getShowDate()) || has(slots.getStartTime())) count++;
        if (slots.getTicketCount() != null && slots.getTicketCount() > 0) count++;
        if (slots.getScheduleId() != null) count++;
        // 历史状态中的也算
        if (state != null) {
            if (has(state.getFilmName()) && !has(slots.getFilmName())) count++;
            if (has(state.getCinemaName()) && !has(slots.getCinemaName())) count++;
            if ((has(state.getShowDate()) || has(state.getStartTime()))
                    && !has(slots.getShowDate()) && !has(slots.getStartTime())) count++;
            if (state.getScheduleId() != null && slots.getScheduleId() == null) count++;
        }
        return count;
    }

    /**
     * 统计合并后总槽位数
     */
    private int countMergedSlots(ConversationState slots, ConversationState state) {
        int count = 0;
        if (has(slots.getFilmName()) || (state != null && has(state.getFilmName()))) count++;
        if (has(slots.getCinemaName()) || (state != null && has(state.getCinemaName()))) count++;
        if (has(slots.getShowDate()) || has(slots.getStartTime())
                || (state != null && (has(state.getShowDate()) || has(state.getStartTime())))) count++;
        if (slots.getTicketCount() != null && slots.getTicketCount() > 0
                || (state != null && state.getTicketCount() != null && state.getTicketCount() > 0)) count++;
        if (slots.getScheduleId() != null || (state != null && state.getScheduleId() != null)) count++;
        if (has(slots.getHallType()) || (state != null && has(state.getHallType()))) count++;
        if (has(slots.getPreferredSeatZone()) || (state != null && has(state.getPreferredSeatZone()))) count++;
        return count;
    }

    private boolean has(String s) { return s != null && !s.isEmpty(); }
    private boolean matches(Pattern p, String s) { return p.matcher(s).find(); }
}
