package com.limou.agent.ai.movie;

import com.limou.agent.ai.StreamChunk;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Sinks;

/** ToolCallback 包装器：在工具调用时向 Sink 发射 tool_start 事件，并兜底 JSON 解析异常。
 *  对 lockSeats / createOrder / payOrder 等工具，还发射 card 事件供前端渲染交互卡片 */
public class EventEmittingToolCallback implements ToolCallback {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EventEmittingToolCallback.class);

    private final ToolCallback delegate;
    private final String displayName;
    private final Sinks.Many<StreamChunk> sink;

    public EventEmittingToolCallback(ToolCallback delegate, String displayName, Sinks.Many<StreamChunk> sink) {
        this.delegate = delegate;
        this.displayName = displayName;
        this.sink = sink;
    }

        @Override
        public String call(String toolInput) {
            String toolName = delegate.getToolDefinition().name();
            sink.tryEmitNext(StreamChunk.toolStart(toolName, displayName));
            try {
                String result = delegate.call(toolInput);
                // ★ 对 card-worthy 工具结果也发射 card 事件给前端
                String cardType = inferCardType(toolName, result);
                if (cardType != null) {
                    sink.tryEmitNext(StreamChunk.card(cardType, result));
                }
                return result;
            } catch (Exception e) {
                log.warn("工具调用参数解析失败: tool={}, error={}",
                        delegate.getToolDefinition().name(), e.getMessage());
                return "{\"error\":\"参数格式错误，请检查 JSON 格式后重试: " + e.getMessage() + "\"}";
            }
        }

        /** 根据 toolName 和结果推断前端卡片类型 */
        private static String inferCardType(String toolName, String result) {
            if (result == null || result.isBlank()) return null;
            boolean success = result.contains("\"success\":true") || result.contains("\"success\": true");
            return switch (toolName) {
                case "searchFilms"      -> "film_list";
                case "searchCinemas"    -> "cinema_list";
                case "searchSchedules"  -> "schedule_list";
                case "searchNearbyCinemas" -> "cinema_list";
                case "getSeatMap"       -> "seat_map";
                case "lockSeats"        -> success ? "seats_confirmed" : "seat_alternatives";
                case "createOrder"      -> success ? "order_detail" : null;
                case "payOrder"         -> success ? "payment_form" : null;
                default                 -> null;
            };
        }

        @Override
        public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }
    }