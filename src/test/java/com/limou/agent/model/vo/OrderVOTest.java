package com.limou.agent.model.vo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderVOTest {

    @Test
    void serializesSnowflakeOrderIdAsString() throws Exception {
        OrderVO order = new OrderVO();
        order.setId(442154216274202625L);

        String json = new ObjectMapper().writeValueAsString(order);

        assertThat(json).contains("\"id\":\"442154216274202625\"");
    }
}
