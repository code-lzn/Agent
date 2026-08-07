package com.limou.agent.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilmReviewVO {

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

    private String userName;
    private String userAvatar;

    /** 是否购票用户 */
    private Boolean isPurchased;

    /** 当前用户是否已点有用 */
    private Boolean isHelpful;
}
