package com.dochelper.agent.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * Agent 会话消息数据库实体。
 */
@TableName("agent_message")
public class AgentMessageEntity {

    @TableId
    private Long id;
    private Long conversationId;
    private Long taskId;
    private String role;
    private String contentRedacted;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContentRedacted() { return contentRedacted; }
    public void setContentRedacted(String contentRedacted) {
        this.contentRedacted = contentRedacted;
    }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
