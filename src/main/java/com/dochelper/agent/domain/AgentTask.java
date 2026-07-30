package com.dochelper.agent.domain;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 持久化 Agent 任务聚合。
 */
public record AgentTask(
        Long id,
        Long projectId,
        Long environmentId,
        Long conversationId,
        String goal,
        AgentTaskStatus status,
        int currentStep,
        int maxSteps,
        int toolCallCount,
        int replanCount,
        List<AgentPlanStep> plan,
        String contextJsonRedacted,
        String resultSummary,
        String errorCode,
        String errorMessage,
        boolean cancelRequested,
        int lockVersion,
        LocalDateTime deadlineAt,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime updatedAt
) {
}
