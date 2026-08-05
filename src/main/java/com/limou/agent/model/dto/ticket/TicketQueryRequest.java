package com.limou.agent.model.dto.ticket;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 电影票核销请求（按取票码）。
 *
 * @author 李振南
 */
@Data
public class TicketQueryRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 取票码（8位数字）
     */
    private String ticketCode;
}
