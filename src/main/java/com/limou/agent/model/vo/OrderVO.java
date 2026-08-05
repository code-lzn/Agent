package com.limou.agent.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

/**
 * 订单视图对象。
 *
 * @author 李振南
 */
@Data
public class OrderVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String orderNo;
    private Long userId;
    private Long scheduleId;
    private String filmName;

    /**
     * 影片海报（通过场次关联影片获取）
     */
    private String posterUrl;

    private String cinemaName;
    private String scheduleTime;
    private String hallName;
    private BigDecimal totalPrice;
    private Integer count;
    private String status;
    private String cancelReason;
    private Date paidAt;
    private Date expireAt;
    private Date createTime;

    /**
     * 座位标签列表（如：["5排6座", "5排7座"]）
     */
    private List<String> seatLabels;

    /**
     * 订单内的票列表（每座位一票，含独立取票码/核销状态）
     */
    private List<TicketVO> tickets;
}
