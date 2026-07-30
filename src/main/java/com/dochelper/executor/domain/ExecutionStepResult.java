package com.dochelper.executor.domain;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 场景中的单步执行结果。
 */
public record ExecutionStepResult(
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
}
