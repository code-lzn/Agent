package com.limou.agent.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 电影票视图对象（含关联订单信息，用于核销查询/核销）。
 *
 * @author 李振南
 */
@Data
public class TicketVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long scheduleId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long seatId;
    private String seatLabel;
    private String ticketCode;

    /**
     * 核销状态: 0-未核销 1-已核销
     */
    private Integer status;
    private LocalDateTime checkedInAt;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long checkedBy;

    /**
     * 关联订单信息（冗余）
     */
    private String orderNo;
    private String orderStatus;
    private String filmName;
    private String cinemaName;
    private String hallName;
    private String scheduleTime;
}
