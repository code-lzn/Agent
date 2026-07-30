package com.limou.agent.ai.movie;

/**
 * GuardRail 检查结果
 */
public record GuardRailResult(boolean allowed, String message) {

    public static GuardRailResult passed() {
        return new GuardRailResult(true, null);
    }

    public static GuardRailResult blocked(String message) {
        return new GuardRailResult(false, message);
    }
}