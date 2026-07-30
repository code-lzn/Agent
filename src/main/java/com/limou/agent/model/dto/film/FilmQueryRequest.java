package com.limou.agent.model.dto.film;

import com.limou.agent.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

/**
 * 影片查询请求。
 *
 * @author 李振南
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class FilmQueryRequest extends PageRequest implements Serializable {

    /**
     * 影片名称（模糊搜索）
     */
    private String keyword;

    /**
     * 影片类型
     */
    private String type;

    /**
     * 状态: draft/published/offline
     */
    private String status;

    @Serial
    private static final long serialVersionUID = 1L;
}
