package com.limou.agent.model.vo;

import com.limou.agent.model.entity.Seat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 座位图视图对象。
 *
 * @author 李振南
 */
@Data
public class SeatMapVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 影厅信息
     */
    private Long hallId;
    private String hallName;
    private String hallType;
    private Integer rowCount;
    private Integer colCount;

    /**
     * 场次信息
     */
    private Long scheduleId;
    private java.math.BigDecimal price;
    private java.math.BigDecimal vipPrice;

    /**
     * 座位列表
     */
    private List<Seat> seats;
}
