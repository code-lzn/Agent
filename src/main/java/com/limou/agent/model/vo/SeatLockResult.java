package com.limou.agent.model.vo;

import com.limou.agent.model.entity.Seat;
import lombok.Data;

import java.util.List;

/**
 * 座位锁定结果。
 *
 * @author 李振南
 */
@Data
public class SeatLockResult {

    /**
     * 是否全部锁定成功
     */
    private boolean success;

    /**
     * 锁定成功的座位（含标签/区域，status 已置为 locked）
     */
    private List<Seat> lockedSeats;

    /**
     * 冲突座位 ID
     */
    private List<Long> conflictSeatIds;

    /**
     * 冲突座位标签（用于提示用户）
     */
    private List<String> conflictSeatLabels;
}
