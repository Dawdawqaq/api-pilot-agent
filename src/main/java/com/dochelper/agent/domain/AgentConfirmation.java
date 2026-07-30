package com.dochelper.agent.domain;

import java.time.LocalDateTime;

/**
 * Agent 危险操作确认记录。
 */
public record AgentConfirmation(
        Long id,
        Long taskId,
        int stepIndex,
        ConfirmationStatus status,
        String requestJson,
        String decisionNote,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        LocalDateTime decidedAt
) {
}
