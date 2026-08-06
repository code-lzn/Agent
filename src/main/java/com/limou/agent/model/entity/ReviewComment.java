package com.limou.agent.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("review_comment")
public class ReviewComment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("reviewId")
    private Long reviewId;

    @Column("userId")
    private Long userId;

    @Column("parentId")
    private Long parentId;

    @Column("replyToUserId")
    private Long replyToUserId;

    private String content;

    @Column("helpfulCount")
    private Integer helpfulCount;

    @Column(value = "isDelete", isLogicDelete = true)
    private Boolean isDelete;

    @Column("createTime")
    private LocalDateTime createTime;
}
