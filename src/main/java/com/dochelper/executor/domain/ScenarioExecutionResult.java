package com.dochelper.executor.domain;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 多步骤 API 场景执行结果。
 */
public record ScenarioExecutionResult(
        Long id,
        Long projectId,
        Long environmentId,
        ExecutionStatus status,
        int stepCount,
        int completedStepCount,
        long durationMs,
        String errorCode,
        String errorMessage,
        List<ExecutionStepResult> steps,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
