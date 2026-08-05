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
     * 横向过道（行间加宽）：这些行【之后】插入过道
     */
    private List<Integer> aisleRows;

    /**
     * 纵向过道（列间加宽）：这些列【之后】插入过道
     */
    private List<Integer> aisleCols;

    /**
     * 每行独立列数（缺省时用 colCount），用于按物理格遍历渲染
     */
    private java.util.Map<Integer, Integer> rowOverrides;

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
