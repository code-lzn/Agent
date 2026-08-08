package com.limou.agent.ai.movie.graph;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.limou.agent.model.dto.movie.ConversationState;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Graph 意图分类器（优化版）
 *
 * 优化：精简 prompt ~90行→~30行 + ChatClient 复用，不额外设置 options
 * （defaultOptions 会覆盖 ChatModel 的 model/api-key 等默认值导致空响应）
 */
@Slf4j
@Component
public class GraphIntentClassifier {

    private static final String INTENT_PROMPT = """
            你是电影票意图识别器。分析用户输入和对话状态，只输出 JSON。

            ## 意图（12 种）
            search_movie | search_cinema | search_nearby | search_schedule | get_seat_map
            | lock_seats | create_order | pay_order | query_order | get_preference | greeting | chat

            ## 区分要点
            - 地点/地标名（大学/广场/火车站/景区→search_nearby，location 填地点名）
            - 影院品牌名（万达/万达影城/CGV→search_cinema，cinemaName 填影院名）
            - 只有出现"买/订/下单/选座/要X张票"才识别为 lock_seats/create_order
            - 用户有 orderId 后问"看看订单/订单状态"→query_order
            - 时段换算：上午→09:00 中午→12:00 下午→14:00 晚上→19:00
            - 用户说"都帮我选/全都要/两个都"表示多部影片，filmName 填逗号分隔

            ## 槽位（有则填，无则 null）
            filmName, cinemaName, location, hallType, showDate(yyyy-MM-dd), startTime(HH:mm),
            ticketCount(整数), seatLabels[], scheduleId, orderId, preferredSeatZone(中间/靠前/靠后)

            ## 当前时间
            今天是 {today}

            ## 对话状态
            {state}

            ## 只输出 JSON（不要 markdown）
            {"intent":"search_movie","slots":{"filmName":"流浪地球3","hallType":"IMAX"},"askPrompt":null}

            ## 用户输入
            {input}
            """;

    @Resource
    private DashScopeChatModel dashscopeChatModel;

    @Resource
    private ObjectMapper objectMapper;

    private ChatClient chatClient;

    /**
     * 意图分类结果缓存 —— 按 "消息摘要:状态摘要" 缓存，避免相同输入重复调用 LLM
     * 过期策略：写入后 5 分钟过期，最多 2000 条
     */
//    private final Cache<String, GraphIntentResult> intentCache = Caffeine.newBuilder()
//            .maximumSize(2000)
//            .expireAfterWrite(Duration.ofMinutes(5))
//            .recordStats()
//            .build();

    @PostConstruct
    public void init() {
        this.chatClient = ChatClient.builder(dashscopeChatModel).build();
    }

    /**
     * 意图识别 + 槽位提取
     */
    public GraphIntentResult classify(String userMessage, ConversationState currentState) {
        // ★ 优化1：缓存命中直接返回，跳过 LLM 调用
//        String cacheKey = buildCacheKey(userMessage, currentState);
//        GraphIntentResult cached = intentCache.getIfPresent(cacheKey);
//        if (cached != null) {
//            log.debug("Graph Intent 缓存命中: key={}", cacheKey);
//            return cached;
//        }

        String stateContext = currentState != null ? currentState.toPromptContext() : "无";
        String today = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd EEEE HH:mm", Locale.CHINA));
        String prompt = INTENT_PROMPT
                .replace("{today}", today)
                .replace("{state}", stateContext)
                .replace("{input}", userMessage);

        try {
            // ★ 优化2：限制 max_tokens=256 + temperature=0，减少生成时间和 token 消耗
            String raw = chatClient.prompt()
                    .user(prompt)
//                    .options(options -> options
//                            .maxOutputTokens(256)    // 意图 JSON 很小，不需要默认的 4096
//                            .temperature(0.0))        // 分类任务不需要随机性
                    .call()
                    .content();
            String json = cleanJson(raw);
            Map<String, Object> result = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});

            String intent = (String) result.getOrDefault("intent", "chat");

            ConversationState slots = new ConversationState();
            @SuppressWarnings("unchecked")
            Map<String, Object> slotsMap = (Map<String, Object>) result.get("slots");
            if (slotsMap != null) {
                if (slotsMap.get("filmName") != null)
                    slots.setFilmName((String) slotsMap.get("filmName"));
                if (slotsMap.get("filmType") != null)
                    slots.setFilmType((String) slotsMap.get("filmType"));
                if ("search_nearby".equals(intent) && slotsMap.get("location") != null) {
                    slots.setCinemaName((String) slotsMap.get("location"));
                } else if (slotsMap.get("cinemaName") != null) {
                    slots.setCinemaName((String) slotsMap.get("cinemaName"));
                }
                if (slotsMap.get("hallType") != null)
                    slots.setHallType((String) slotsMap.get("hallType"));
                if (slotsMap.get("showDate") != null)
                    slots.setShowDate((String) slotsMap.get("showDate"));
                if (slotsMap.get("startTime") != null)
                    slots.setStartTime((String) slotsMap.get("startTime"));
                if (slotsMap.get("ticketCount") != null) {
                    Object tc = slotsMap.get("ticketCount");
                    slots.setTicketCount(tc instanceof Integer ? (Integer) tc : Integer.parseInt(tc.toString()));
                }
                if (slotsMap.get("scheduleId") != null) {
                    Object sid = slotsMap.get("scheduleId");
                    slots.setScheduleId(sid instanceof Long ? (Long) sid : Long.valueOf(sid.toString()));
                }
                if (slotsMap.get("orderId") != null) {
                    Object oid = slotsMap.get("orderId");
                    slots.setOrderId(oid instanceof Long ? (Long) oid : Long.valueOf(oid.toString()));
                }
                if (slotsMap.get("preferredSeatZone") != null)
                    slots.setPreferredSeatZone((String) slotsMap.get("preferredSeatZone"));
            }
//, cacheKey, cacheKey={}
            log.info("Graph Intent: intent={}, slots={}", intent, slots);
            GraphIntentResult intentResult = GraphIntentResult.builder()
                    .intent(intent)
                    .slots(slots)
                    .askPrompt((String) result.get("askPrompt"))
                    .build();

            // ★ 写入缓存
//            intentCache.put(cacheKey, intentResult);
            return intentResult;
        } catch (Exception e) {
            log.error("意图识别失败，降级为 chat", e);
            GraphIntentResult fallback = GraphIntentResult.builder()
                    .intent("chat")
                    .slots(new ConversationState())
                    .build();
            // ★ 异常结果也短期缓存，避免重复失败调用（1 分钟）
//            intentCache.put(cacheKey, fallback);
            return fallback;
        }
    }

    /**
     * 构建缓存 key：消息摘要 + 关键槽位变化
     * 注意：不含时间（today），同一消息不同秒数应命中缓存
     */
//    private String buildCacheKey(String userMessage, ConversationState state) {
//        String msg = userMessage != null ? userMessage.trim() : "";
//        String stateKey = "";
//        if (state != null) {
//            // 只用影响意图的关键字段
//            stateKey = (state.getFilmId() != null ? "f" + state.getFilmId() : "")
//                    + (state.getCinemaId() != null ? "c" + state.getCinemaId() : "")
//                    + (state.getScheduleId() != null ? "s" + state.getScheduleId() : "")
//                    + (state.getOrderId() != null ? "o" + state.getOrderId() : "");
//        }
//        return msg.hashCode() + ":" + stateKey;
//    }

    private String cleanJson(String raw) {
        if (raw == null || raw.isBlank()) return "{}";
        String json = raw.trim();
        if (json.startsWith("```json"))
            json = json.substring(7);
        else if (json.startsWith("```"))
            json = json.substring(3);
        if (json.endsWith("```"))
            json = json.substring(0, json.length() - 3);
        return json.trim();
    }
}
