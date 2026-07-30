package com.limou.agent.ai.movie;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 电影票 Agent 输入防护栏
 * 在用户消息进入 LLM 之前进行安全校验，防止 prompt injection 和异常输入
 */
@Slf4j
@Component
public class MovieGuardRail {

    private static final int MAX_INPUT_LENGTH = 2000;
    private static final int MIN_INPUT_LENGTH = 1;

    /** 已知的 prompt injection 攻击模式 */
    private static final Pattern[] INJECTION_PATTERNS = {
            Pattern.compile("ignore\\s+(previous|all|above)\\s+(instructions?|prompts?|rules?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("forget\\s+(everything|all|your)\\s+(you\\s+know|instructions?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you\\s+are\\s+now\\s+(a|an)\\s+\\w+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("pretend\\s+(to\\s+be|you\\s+are)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<\\|im_start\\|>|<\\|im_end\\|>|<\\|system\\|>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\[system\\]|\\[assistant\\]|\\[user\\]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("system\\s*:\\s*you\\s+are", Pattern.CASE_INSENSITIVE),
            Pattern.compile("DAN\\s*:|do\\s+anything\\s+now", Pattern.CASE_INSENSITIVE),
            Pattern.compile("new\\s+system\\s+prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("reveal\\s+(your|the)\\s+(system\\s+)?prompt", Pattern.CASE_INSENSITIVE),
    };

    /** 危险字符重复模式（连续重复字符超过阈值） */
    private static final Pattern REPETITIVE_PATTERN = Pattern.compile("(.)\\1{50,}");

    /**
     * 检查用户输入是否安全
     *
     * @param input 用户输入
     * @return 检查结果
     */
    public GuardRailResult check(String input) {
        // 空值检查
        if (input == null || input.isBlank()) {
            log.warn("GuardRail 拦截: 输入为空");
            return GuardRailResult.blocked("请告诉我您想看什么电影～");
        }

        // 长度检查
        if (input.length() < MIN_INPUT_LENGTH) {
            log.warn("GuardRail 拦截: 输入过短");
            return GuardRailResult.blocked("请告诉我您想看什么电影～");
        }

        if (input.length() > MAX_INPUT_LENGTH) {
            log.warn("GuardRail 拦截: 输入过长, length={}", input.length());
            return GuardRailResult.blocked("您的输入太长了，请精简后重试～");
        }

        // 重复字符检查
        if (REPETITIVE_PATTERN.matcher(input).find()) {
            log.warn("GuardRail 拦截: 检测到重复字符模式");
            return GuardRailResult.blocked("输入包含异常字符，请重新输入～");
        }

        // prompt injection 模式检查
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                log.warn("GuardRail 拦截: 检测到 prompt injection 模式, pattern={}", pattern.pattern());
                return GuardRailResult.blocked("抱歉，无法处理该请求，请重新输入～");
            }
        }

        return GuardRailResult.passed();
    }
}