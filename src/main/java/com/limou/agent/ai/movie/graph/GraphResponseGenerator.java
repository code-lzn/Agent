package com.limou.agent.ai.movie.graph;

import com.limou.agent.model.dto.movie.ConversationState;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.stereotype.Component;

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

            ## 当前意图
            {intent}

            ## 工具执行结果
            {tool_result}

            ## 对话状态
            {state}

            ## 用户输入
            {input}

            ## 要求
            根据工具执行结果生成自然回复。
            - 如果工具返回了数据，用友好的语气呈现给用户
            - 如果工具执行失败，安慰用户并给出建议
            - 如果是问候，热情回应
            - 如果是未知意图，引导用户说明需求
            """;

    @Resource
    private DeepSeekChatModel chatModel;

    /**
     * 生成回复
     */
    public String generate(String intent, String userMessage, String toolResult, ConversationState state) {
        String stateContext = state != null ? state.toPromptContext() : "无";
        String prompt = RESPONSE_PROMPT
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