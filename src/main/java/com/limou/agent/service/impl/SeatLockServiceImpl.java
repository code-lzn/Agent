package com.limou.agent.service.impl;

import com.limou.agent.mapper.SeatMapper;
import com.limou.agent.model.entity.Seat;
import com.limou.agent.model.vo.SeatLockResult;
import com.limou.agent.service.SeatLockService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 座位锁定服务实现：Redis 分布式锁（互斥）+ 数据库乐观锁（只锁 available）。
 * <p>
 * 相比 FOR UPDATE 行锁：行锁是阻塞等待，Redis 锁是快速失败（tryLock 3 秒拿不到即返回冲突）；
 * 乐观锁保证同一座位只有第一个把 status 从 available 改成 locked 的人成功。
 *
 * @author 李振南
 */
@Service
@Slf4j
public class SeatLockServiceImpl implements SeatLockService {

    private static final String LOCK_KEY_PREFIX = "seat:lock:";
    private static final long WAIT_SECONDS = 3;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private SeatMapper seatMapper;

    @Override
    public SeatLockResult lockSeats(Long scheduleId, List<Long> seatIds, int leaseMinutes) {
        SeatLockResult result = new SeatLockResult();
        if (scheduleId == null || seatIds == null || seatIds.isEmpty()) {
            result.setSuccess(false);
            return result;
        }

        // 1. 查询座位（校验存在 + 拿标签/区域）
        List<Seat> seats = seatMapper.selectListByQuery(
                QueryWrapper.create().eq("scheduleId", scheduleId).in("id", seatIds));
        if (seats.size() != new HashSet<>(seatIds).size()) {
            result.setSuccess(false);
            result.setConflictSeatIds(seatIds);
            result.setConflictSeatLabels(List.of("部分座位不存在，请刷新后重试"));
            return result;
        }

        // 2. 按 ID 排序，避免多座位并发死锁
        List<Seat> sorted = seats.stream()
                .sorted(Comparator.comparing(Seat::getId))
                .collect(Collectors.toList());

        // 3. 获取所有 Redis 锁（任一失败则释放已拿的并返回冲突）
        List<RLock> acquiredLocks = new ArrayList<>();
        try {
            for (Seat seat : sorted) {
                RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + scheduleId + ":" + seat.getId());
                if (lock.tryLock(WAIT_SECONDS, leaseMinutes * 60L, TimeUnit.SECONDS)) {
                    acquiredLocks.add(lock);
                } else {
                    releaseLocks(acquiredLocks);
                    result.setSuccess(false);
                    result.setConflictSeatIds(List.of(seat.getId()));
                    result.setConflictSeatLabels(List.of(seat.getSeatLabel() + " 正在被他人锁定"));
                    return result;
                }
            }

            // 4. 乐观锁逐个更新：只允许 available → locked
            List<Seat> locked = new ArrayList<>();
            List<Long> conflictIds = new ArrayList<>();
            List<String> conflictLabels = new ArrayList<>();
            for (Seat seat : sorted) {
                int updated = seatMapper.updateByQuery(
                        Seat.builder().status("locked").build(),
                        QueryWrapper.create()
                                .eq("id", seat.getId())
                                .eq("status", "available"));
                if (updated > 0) {
                    seat.setStatus("locked");
                    locked.add(seat);
                } else {
                    conflictIds.add(seat.getId());
                    conflictLabels.add(seat.getSeatLabel() + " 已被占用");
                }
            }

            if (!conflictIds.isEmpty()) {
                // 回滚本次已锁定的座位（保持 DB 一致），并释放全部 Redis 锁
                for (Seat seat : locked) {
                    seatMapper.updateByQuery(
                            Seat.builder().status("available").build(),
                            QueryWrapper.create().eq("id", seat.getId()).eq("status", "locked"));
                }
                releaseLocks(acquiredLocks);
                result.setSuccess(false);
                result.setConflictSeatIds(conflictIds);
                result.setConflictSeatLabels(conflictLabels);
                return result;
            }

            result.setSuccess(true);
            result.setLockedSeats(locked);
            return result;
        } catch (Exception e) {
            log.error("Redis 锁座异常 scheduleId={}, seats={}", scheduleId, seatIds, e);
            releaseLocks(acquiredLocks);
            result.setSuccess(false);
            result.setConflictSeatLabels(List.of("锁座失败，请稍后重试"));
            return result;
        }
    }

    @Override
    public void releaseSeats(Long scheduleId, List<Long> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) {
            return;
        }
        for (Long seatId : seatIds) {
            try {
                redissonClient.getLock(LOCK_KEY_PREFIX + scheduleId + ":" + seatId).forceUnlock();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void releaseSeatsToAvailable(Long scheduleId, List<Long> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) {
            return;
        }
        for (Long seatId : seatIds) {
            seatMapper.updateByQuery(
                    Seat.builder().status("available").build(),
                    QueryWrapper.create().eq("id", seatId).eq("status", "locked"));
        }
        releaseSeats(scheduleId, seatIds);
    }

    private void releaseLocks(List<RLock> locks) {
        for (RLock lock : locks) {
            try {
                lock.forceUnlock();
            } catch (Exception ignored) {
            }
        }
    }
}
