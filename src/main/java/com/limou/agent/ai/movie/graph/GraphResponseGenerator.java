package com.limou.agent.ai.movie.graph;

import com.limou.agent.model.dto.movie.ConversationState;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Graph 回复生成器
 * 仅用 LLM 生成自然语言回复，不持有工具
 * 输入：意图 + 工具结果 + 对话状态
 * 输出：自然语言回复文本
 */
@Slf4j
@Component
public class GraphResponseGenerator {

    public static final String RESPONSE_PROMPT = """
            你是一个电影票智能助手，名叫"小影"。

            ## 角色
            你的语气自然、亲切，像朋友聊天一样，适当使用 emoji。
            不要提到你是 AI 或大模型。
            每次回复控制在 200 字以内。

            ## 重要：前端页面交互须知
            - 选座页面（/seat）上，用户选好座位后点击底部「确认选座」按钮即可完成锁座和下单，系统自动处理两步操作
            - **绝对不要**让用户去找"锁定"按钮——页面上没有这个按钮
            - 当用户已在选座页面时，引导话术应为："选好座位后点击底部的「确认选座」就行啦～"

            ## 当前日期
            今天是：{today}（含星期）。用户问到日期/星期/时间相关问题时据此回答。

            ## 当前意图
            {intent}

            ## 工具执行结果
            {tool_result}

            ## 对话状态
            {state}

            ## 用户输入
            {input}

            ## ★ 回复规则（极其重要！）
            1. **先看对话状态**：回复前必须先检查对话状态中是否有已选影片/影院/场次/座位/订单等信息。
               如果有人在何已确认的信息，在回复中自然地提及，不要假装不知道。
               例如：状态中有"已选座位: 9排5座"和"当前订单ID: xxx"，用户说"在干嘛"，
               你应该说"刚帮你搞定了《xxx》9排5座的票～有啥需要随时叫我 😊"而不是空洞的"我在呀"。
            2. **结合状态回复**：不管意图是 chat/greeting/unknown，都要结合对话状态中的已有信息来回复。
            3. **不要无视状态**：对话状态中的信息就是已经发生的真实情况，不要在回复中表现出"不知道"的态度。
            4. **状态为空时才引导**：只有对话状态中没有任何信息时，才用引导话术让用户说明需求。
            5. **工具结果处理**：如果工具返回了数据，用友好的语气呈现给用户；如果工具执行失败，安慰用户并给出建议。
            6. **问候处理**：如果是问候且状态为空，热情回应并引导用户。
            7. **严禁编造**：票数/座位/价格/时间/影院等信息必须以工具结果或对话状态为准，缺失时如实告知并询问用户，不要臆测默认值（如"默认两张"）或编造具体座位号。
            """;

    @Resource
    private DeepSeekChatModel chatModel;

    /**
     * 生成回复
     */
    public String generate(String intent, String userMessage, String toolResult, ConversationState state) {
        String stateContext = state != null ? state.toPromptContext() : "无";
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd EEEE HH:mm", Locale.CHINA));
        String prompt = RESPONSE_PROMPT
                .replace("{today}", today)
                .replace("{intent}", intent != null ? intent : "chat")
                .replace("{tool_result}", toolResult != null && !toolResult.isEmpty() ? toolResult : "无工具结果")
                .replace("{state}", stateContext)
                .replace("{input}", userMessage);

        try {
            String response = ChatClient.builder(chatModel).build()
                    .prompt().user(prompt).call().content();
            return response != null ? response : "收到啦～让我帮您看看～";
        } catch (Exception e) {
            log.error("回复生成失败", e);
            return "抱歉，出了一点小问题，请稍后再试～";
        }
    }
}