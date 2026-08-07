package com.limou.agent.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyReviewVO {

    private Long id;
    private Long userId;
    private Long filmId;
    private Long orderId;
    private Integer rating;
    private String content;
    private String tags;
    private Integer helpfulCount;
    private Integer commentCount;
    private LocalDateTime createTime;

    private String filmName;
    private String filmPosterUrl;
    private Boolean isPurchased;

    /** 谁点过有用 */
    private List<UserBriefVO> helpfulUsers;

    /** 评论列表（含内容） */
    private List<CommentBriefVO> comments;
}
