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
public class ReviewCommentVO {

    private Long id;
    private Long reviewId;
    private Long userId;
    private Long parentId;
    private String content;
    private Integer helpfulCount;
    private LocalDateTime createTime;

    private String userName;
    private String userAvatar;
    private String replyToUserName;
    private Boolean isHelpful;
}
