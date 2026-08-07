package com.limou.agent.mapper;

import com.limou.agent.model.entity.FilmReview;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface FilmReviewMapper extends BaseMapper<FilmReview> {

    /** 绕过逻辑删除过滤，查是否有过任意记录 */
    @Select("SELECT * FROM film_review WHERE userId = #{userId} AND filmId = #{filmId} LIMIT 1")
    FilmReview findAnyByUserAndFilm(@Param("userId") Long userId, @Param("filmId") Long filmId);

    /** 绕过逻辑删除过滤，恢复已删记录并更新内容（计数清零） */
    @Update("UPDATE film_review SET isDelete = 0, orderId = #{orderId}, rating = #{rating}, content = #{content}, tags = #{tags}, helpfulCount = 0, commentCount = 0, createTime = NOW() WHERE id = #{id}")
    int reviveReview(@Param("id") Long id, @Param("orderId") Long orderId,
                     @Param("rating") Integer rating, @Param("content") String content,
                     @Param("tags") String tags);
}
