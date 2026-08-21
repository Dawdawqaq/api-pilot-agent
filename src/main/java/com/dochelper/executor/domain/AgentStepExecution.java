package com.dochelper.executor.domain;

import java.time.LocalDateTime;

/**
 * Agent 单步执行与恢复记录。
 */
public record AgentStepExecution(
        Long id,
        Long taskId,
        Long executionId,
        int stepIndex,
        int attempt,
        StepExecutionStatus status,
        String requestFingerprint,
        String idempotencyKeyHash,
        String inputVariableNamesJson,
        String outputSecretRef,
        Integer responseStatus,
        ExecutionErrorCategory errorCategory,
        String errorCode,
        String errorMessage,
        Long durationMs,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
