package com.limou.agent.ai;


import com.limou.agent.ai.tools.ToolManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class AgentServiceFactory {
    @Resource
    private DeepSeekChatModel deepseekChatModel;
    @Resource
    private ToolManager toolManager;
    @Resource
    private ChatClient chatClient;
//    caffine   本地缓存的优化
//        private final Cache<String, AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
//            .maximumSize(1000)
//            .expireAfterWrite(Duration.ofMinutes(30))
//            .expireAfterAccess(Duration.ofMinutes(10))
//            .removalListener((key, value, cause) -> {
//                log.debug("AI 服务实例被移除，缓存键: {}, 原因: {}", key, cause);
//            })
//            .build();

//    构造

//     log.info("为 appId: {} 创建新的 AI 服务实例", appId);

//        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
//                .builder()
//                .id(appId)
//                .chatMemoryStore(redisChatMemoryStore)
//                .maxMessages(20)
//                .build();
//        chatHistoryService.loadChatHistory(appId, chatMemory, 20);

    @PostConstruct
    public void init() {
        String systemPrompt = loadSystemPrompt();
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(10)
                .build();

        chatClient = ChatClient.builder(deepseekChatModel)
                .defaultSystem(systemPrompt)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                        // 自定义日志 Advisor，可按需开启
//                        // 自定义推理增强 Advisor，可按需开启
//                       ,new ReReadingAdvisor()
                )
                .build();
    }

    /**
     * AI 基础对话（支持多轮对话记忆）
     *
     * @param message
     * @param
     * @return, String chatId
     */
    public String doChat(String message) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
//                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
//                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
//        .chatResponse() —— 提取原始的 ChatResponse 对象
//        .call() → 真正向 AI 模型发送请求（同步阻塞），等待模型返回完整响应后，返回一个 CallResponseSpec 对象。
    }

    private String loadSystemPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/system-prompt.md");
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("加载 system-prompt.md 失败，使用默认提示词", e);
            return "你是一个高级开发工程师。";
        }
    }
}
