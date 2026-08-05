package com.limou.agent.ai.movie;

/**
 * 会话上下文持有器（ThreadLocal）
 * <p>
 * ReAct 模式下，LLM 通过 Spring AI 框架直接调用 {@code @Tool} 方法，
 * 工具方法签名里没有 conversationId 参数。通过此 ThreadLocal，
 * reactCore 在执行前注入 conversationId，工具方法在执行时取出，
 * 从而将关键结果（orderId、seatIds 等）写回 ConversationState。
 * <p>
 * 用法：
 * <pre>{@code
 * ConversationContext.set(conversationId);
 * try { ... } finally { ConversationContext.clear(); }
 * }</pre>
 * <p>
 * Graph 模式下此 ThreadLocal 不会被设置，工具由 Node 调用，
 * Node 已自行处理状态写回，不会冲突。
 */
public final class ConversationContext {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private ConversationContext() {}

    /** 注入当前请求的 conversationId */
    public static void set(String conversationId) {
        HOLDER.set(conversationId);
    }

    /** 获取当前请求的 conversationId（ReAct 模式下非 null，Graph 模式下为 null） */
    public static String get() {
        return HOLDER.get();
    }

    /** 清理，防止内存泄漏 */
    public static void clear() {
        HOLDER.remove();
    }
}
