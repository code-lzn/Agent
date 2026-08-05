package com.limou.agent.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对话状态持久化 — 防 Redis 重启/TTL 过期丢失上下文
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("conversation_state")
public class ConversationStateEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /** 会话ID（对应 chat_session.id） */
    @Column("conversationId")
    private String conversationId;

    /** ConversationState 序列化 JSON */
    @Column("stateJson")
    private String stateJson;

    @Column("userId")
    private Long userId;

    @Column("createTime")
    private LocalDateTime createTime;

    @Column("updateTime")
    private LocalDateTime updateTime;
}
