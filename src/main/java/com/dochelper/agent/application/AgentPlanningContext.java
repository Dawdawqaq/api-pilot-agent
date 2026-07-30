package com.dochelper.agent.application;

import java.util.List;
import java.util.Set;

import com.dochelper.executor.api.dto.ExecutionStepRequest;
import com.dochelper.openapi.domain.ApiEndpoint;
import com.dochelper.retrieval.domain.RetrievalResult;

/**
 * Agent 结构化规划上下文。
 */
public record AgentPlanningContext(
        Long taskId,
        Long projectId,
        String goal,
        List<RetrievalResult> evidence,
        List<ApiEndpoint> endpoints,
        Set<String> initialVariableNames,
        List<ExecutionStepRequest> planHint
) {
}
