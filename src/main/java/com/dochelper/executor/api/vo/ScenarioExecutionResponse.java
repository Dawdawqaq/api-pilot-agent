package com.dochelper.executor.api.vo;

import java.time.LocalDateTime;
import java.util.List;

import com.dochelper.executor.domain.ExecutionStatus;
import com.dochelper.executor.domain.ScenarioExecutionResult;

/**
 * 多步骤 API 场景执行响应。
 */
public record ScenarioExecutionResponse(
        Long id,
        Long projectId,
        Long environmentId,
        ExecutionStatus status,
        int stepCount,
        int completedStepCount,
        long durationMs,
        String errorCode,
        String errorMessage,
        List<ExecutionStepResponse> steps,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {

    public static ScenarioExecutionResponse from(ScenarioExecutionResult value) {
        return new ScenarioExecutionResponse(
                value.id(),
                value.projectId(),
                value.environmentId(),
                value.status(),
                value.stepCount(),
                value.completedStepCount(),
                value.durationMs(),
                value.errorCode(),
                value.errorMessage(),
                value.steps().stream().map(ExecutionStepResponse::from).toList(),
                value.createdAt(),
                value.completedAt()
        );
    }
}
