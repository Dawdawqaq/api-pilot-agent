package com.dochelper.executor.api.vo;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.dochelper.executor.domain.AssertionResult;
import com.dochelper.executor.domain.ExecutionStepResult;

/**
 * 场景执行步骤响应。
 */
public record ExecutionStepResponse(
        int stepIndex,
        String name,
        String method,
        String requestUrl,
        int responseStatus,
        JsonNode responseBody,
        Map<String, Object> extractedVariables,
        List<AssertionResult> assertions,
        boolean success,
        long durationMs,
        String errorMessage
) {

    public static ExecutionStepResponse from(ExecutionStepResult value) {
        return new ExecutionStepResponse(
                value.stepIndex(),
                value.name(),
                value.method(),
                value.requestUrl(),
                value.responseStatus(),
                value.responseBody(),
                value.extractedVariables(),
                value.assertions(),
                value.success(),
                value.durationMs(),
                value.errorMessage()
        );
    }
}
