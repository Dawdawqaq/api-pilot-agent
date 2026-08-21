package com.dochelper.agent.application;

import java.util.List;
import java.util.Set;

import com.dochelper.agent.domain.AgentPlanStep;
import com.dochelper.executor.domain.ExecutionStepResult;
import com.dochelper.openapi.domain.ApiEndpoint;

/**
 * 根据已执行观察结果生成剩余计划的上下文。
 */
public record AgentReplanContext(
        Long taskId,
        Long projectId,
        String originalGoal,
        List<AgentPlanStep> originalPlan,
        List<ExecutionStepResult> completedSteps,
        Set<String> availableVariableNames,
        String failureCode,
        String failureMessage,
        List<ApiEndpoint> remainingEndpoints
) {
}
