package com.limou.agent.ai;

import com.limou.agent.ai.AgentServiceFactory;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AgentApplicationTests {
@Resource
private AgentServiceFactory agentServiceFactory;
    @Test
    void contextLoads() {
        agentServiceFactory.doChat("你好河南科技大学附近有啥","1");
    }

}
