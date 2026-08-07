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
public class CommentBriefVO {

    private Long userId;
    private String userName;
    private String userAvatar;
    private String content;
    private LocalDateTime createTime;
}
