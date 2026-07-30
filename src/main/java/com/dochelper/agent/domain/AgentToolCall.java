package com.dochelper.agent.domain;

import java.time.LocalDateTime;

/**
 * Agent 工具调用审计记录。
 */
public record AgentToolCall(
        Long id,
        Long taskId,
        int stepIndex,
        String toolName,
        String callKey,
        String requestJsonRedacted,
        String responseJsonRedacted,
        ToolCallStatus status,
        int attempt,
        Long durationMs,
        String errorCode,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
