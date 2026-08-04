package com.limou.agent.mapper;

import com.mybatisflex.core.BaseMapper;
import com.limou.agent.model.entity.Seat;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 映射层。
 *
 * @author 李振南
 */
public interface SeatMapper extends BaseMapper<Seat> {

    /**
     * 多行批量插入座位（单条 SQL、一次网络往返）。
     * 避免 saveBatch 在自增主键下逐行插入导致 N 次网络往返（远程库实测约 50ms/行）。
     * 省略 isDelete/createTime/updateTime，由数据库默认值填充。
     */
    @Insert("<script>" +
            "INSERT INTO seat (scheduleId, hallId, rowNum, colNum, seatLabel, zone, status) VALUES " +
            "<foreach collection='list' item='s' separator=','>" +
            "(#{s.scheduleId}, #{s.hallId}, #{s.rowNum}, #{s.colNum}, #{s.seatLabel}, #{s.zone}, #{s.status})" +
            "</foreach>" +
            "</script>")
    int batchInsertSeats(@Param("list") List<Seat> seats);
}
