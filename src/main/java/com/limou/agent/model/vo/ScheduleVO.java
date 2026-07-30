package com.limou.agent.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Time;
import java.time.LocalDateTime;
import java.sql.Date;

/**
 * 排期视图对象（含关联名称）。
 *
 * @author 李振南
 */
@Data
public class ScheduleVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long filmId;
    private Long cinemaId;
    private Long hallId;
    private Date showDate;
    private Time startTime;
    private Time endTime;
    private BigDecimal price;
    private BigDecimal vipPrice;
    private String status;

    // 关联名称
    private String filmName;
    private String filmPoster;
    private Integer filmDuration;
    private String filmRating;
    private String filmType;
    private String cinemaName;
    private String cinemaAddress;
    private String hallName;
    private String hallType;
    private Integer hallRowCount;
    private Integer hallColCount;
}
