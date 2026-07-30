package com.limou.agent.model.dto.schedule;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Date;

/**
 * 排期冲突校验请求。
 *
 * @author 李振南
 */
@Data
public class ConflictCheckRequest implements Serializable {

    /**
     * 影厅ID
     */
    private Long hallId;

    /**
     * 放映日期
     */
    private Date showDate;

    /**
     * 开始时间 (HH:mm)
     */
    private String startTime;

    /**
     * 结束时间 (HH:mm)
     */
    private String endTime;

    /**
     * 排除的场次ID（编辑时排除自身）
     */
    private Long excludeScheduleId;

    @Serial
    private static final long serialVersionUID = 1L;
}
