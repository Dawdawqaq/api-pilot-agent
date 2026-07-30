package com.dochelper.agent.domain;

import java.time.LocalDateTime;

/**
 * Agent 会话。
 */
public record AgentConversation(
        Long id,
        Long projectId,
        String title,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
