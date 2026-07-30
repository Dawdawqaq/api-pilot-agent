package com.dochelper.executor.domain;

import java.time.LocalDateTime;

/**
 * 场景执行审计主记录。
 */
public record ExecutionAudit(
        Long id,
        Long projectId,
        Long environmentId,
        ExecutionStatus status,
        int stepCount,
        int completedStepCount,
        Long durationMs,
        String errorCode,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
