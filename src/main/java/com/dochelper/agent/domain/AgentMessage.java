package com.dochelper.agent.domain;

import java.time.LocalDateTime;

/**
 * Agent 会话消息。
 */
public record AgentMessage(
        Long id,
        Long conversationId,
        Long taskId,
        String role,
        String contentRedacted,
        LocalDateTime createdAt
) {
}
