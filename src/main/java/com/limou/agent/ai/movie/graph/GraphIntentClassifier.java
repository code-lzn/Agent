package com.limou.agent.ai.movie.graph;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limou.agent.model.dto.movie.ConversationState;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Graph 意图分类器
 * 仅使用 LLM 做意图识别 + 槽位提取，不持有任何工具
 * LLM 输出结构化 JSON，代码据此路由到具体工具执行节点
 */
@Slf4j
@Component
public class GraphIntentClassifier {

    private static final String INTENT_PROMPT = """
            你是一个电影票意图识别器。分析用户输入和当前对话状态，输出 JSON。

            ## 意图类型
            - search_movie: 用户想搜索/了解影片
            - search_cinema: 用户想搜索影院
            - search_schedule: 用户想找场次
            - get_seat_map: 用户想看座位
            - lock_seats: 用户想锁定座位
            - create_order: 用户想创建订单
            - pay_order: 用户想支付
            - get_preference: 用户说"老样子"等
            - greeting: 问候/打招呼
            - chat: 一般对话
            - unknown: 无法识别

            ## 槽位提取规则
            从用户输入中提取以下字段，有则填，无则 null：
            - filmName: 影片名称
            - filmType: 影片类型
            - cinemaName: 影院名称
            - hallType: 厅型（IMAX/杜比/VIP 等）
            - showDate: 日期 yyyy-MM-dd
            - startTime: 时间 HH:mm
            - ticketCount: 票数（整数）
            - seatLabels: 座位标签数组，如 ["5排6座"]
            - scheduleId: 场次ID（整数）
            - orderId: 订单ID（整数）
            - preferredSeatZone: 偏好座位区域（中间/靠前/靠后）

            ## 选择动作与时段识别（重要）
            - 用户说"选X厅/选X影院/选第几个/就选这个/那个/确认/可以"等选择或确认动作时，务必提取 hallType/hallName/cinemaName，意图识别为 get_seat_map 或 lock_seats
            - "上午/中午/下午/晚上/凌晨"等时段词，换算成 startTime 填入（上午→09:00，中午→12:00，下午→14:00，晚上→19:00），供场次时间过滤

            ## 当前日期
            今天是：{today}（含星期）。用户提到"今天/明天/后天/某月某日/周几/几点/下午/晚上"等相对时间时，据此换算成具体日期填入 showDate/startTime。

            ## 当前对话状态
            {state}

            ## 输出格式（严格JSON，不要markdown包裹）
            {"intent":"search_movie","slots":{"filmName":"流浪地球3","hallType":"IMAX"},"askPrompt":null}

            ## 用户输入
            {input}
            """;

    @Resource
    private DeepSeekChatModel chatModel;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 意图识别 + 槽位提取
     */
    public GraphIntentResult classify(String userMessage, ConversationState currentState) {
        String stateContext = currentState != null ? currentState.toPromptContext() : "无历史状态";
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd EEEE HH:mm", Locale.CHINA));
        String prompt = INTENT_PROMPT
                .replace("{today}", today)
                .replace("{state}", stateContext)
                .replace("{input}", userMessage);
        //todo 结构化输出优化
        try {
            String raw = ChatClient.builder(chatModel).build()
                    .prompt().user(prompt).call().content();

            String json = cleanJson(raw);//json解析
            Map<String, Object> result = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {
                    });

            String intent = (String) result.getOrDefault("intent", "chat");

            ConversationState slots = new ConversationState();
            @SuppressWarnings("unchecked")
            Map<String, Object> slotsMap = (Map<String, Object>) result.get("slots");
            if (slotsMap != null) {
                if (slotsMap.get("filmName") != null)
                    slots.setFilmName((String) slotsMap.get("filmName"));
                if (slotsMap.get("filmType") != null)
                    slots.setFilmType((String) slotsMap.get("filmType"));
                if (slotsMap.get("cinemaName") != null)
                    slots.setCinemaName((String) slotsMap.get("cinemaName"));
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

            log.info("Graph Intent: intent={}, slots={}", intent, slots);
            return GraphIntentResult.builder()
                    .intent(intent)
                    .slots(slots)
                    .askPrompt((String) result.get("askPrompt"))
                    .build();
        } catch (Exception e) {
            log.error("意图识别失败，降级为 chat", e);
            return GraphIntentResult.builder()
                    .intent("chat")
                    .slots(new ConversationState())
                    .build();
        }
    }

    private String cleanJson(String raw) {
        String json = raw.trim();
        if (json.startsWith("```json"))
            json = json.substring(7);
        if (json.startsWith("```"))
            json = json.substring(3);
        if (json.endsWith("```"))
            json = json.substring(0, json.length() - 3);
        return json.trim();
    }
}