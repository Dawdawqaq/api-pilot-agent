package com.dochelper.agent.api.vo;

import java.time.LocalDateTime;

import com.dochelper.agent.domain.AgentConfirmation;

/**
 * 危险操作确认响应。
 */
public record AgentConfirmationResponse(
        Long id,
        int stepIndex,
        String status,
        String decisionNote,
        Long decidedByUserId,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        LocalDateTime decidedAt
) {

    public static AgentConfirmationResponse from(AgentConfirmation value) {
        return new AgentConfirmationResponse(
                value.id(),
                value.stepIndex(),
                value.status().name(),
                value.decisionNote(),
                value.decidedByUserId(),
                value.expiresAt(),
                value.createdAt(),
                value.decidedAt()
        );
    }
}
