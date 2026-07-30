package com.dochelper.agent.api.vo;

import java.time.LocalDateTime;
import java.util.List;

import com.dochelper.agent.domain.AgentPlanStep;
import com.dochelper.agent.domain.AgentTask;

/**
 * Agent 任务详情响应。
 */
public record AgentTaskResponse(
        Long id,
        Long projectId,
        Long environmentId,
        Long conversationId,
        String goal,
        String status,
        int currentStep,
        int maxSteps,
        int toolCallCount,
        int replanCount,
        List<AgentPlanStep> plan,
        String resultSummary,
        String errorCode,
        String errorMessage,
        boolean cancelRequested,
        AgentConfirmationResponse confirmation,
        List<AgentToolCallResponse> toolCalls,
        List<AgentModelCallResponse> modelCalls,
        LocalDateTime deadlineAt,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime updatedAt
) {

    public static AgentTaskResponse from(
            AgentTask task,
            AgentConfirmationResponse confirmation,
            List<AgentToolCallResponse> toolCalls,
            List<AgentModelCallResponse> modelCalls
    ) {
        return new AgentTaskResponse(
                task.id(),
                task.projectId(),
                task.environmentId(),
                task.conversationId(),
                task.goal(),
                task.status().name(),
                task.currentStep(),
                task.maxSteps(),
                task.toolCallCount(),
                task.replanCount(),
                task.plan(),
                task.resultSummary(),
                task.errorCode(),
                task.errorMessage(),
                task.cancelRequested(),
                confirmation,
                toolCalls,
                modelCalls,
                task.deadlineAt(),
                task.createdAt(),
                task.startedAt(),
                task.completedAt(),
                task.updatedAt()
        );
    }
}
