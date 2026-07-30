package com.dochelper.report.api.vo;

import java.time.LocalDateTime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dochelper.report.domain.TestReportStep;

/**
 * 测试报告步骤响应。
 */
public record TestReportStepResponse(
        Long id,
        int stepIndex,
        String stepName,
        String httpMethod,
        String requestUrl,
        JsonNode requestHeaders,
        JsonNode requestBody,
        Integer responseStatus,
        JsonNode responseHeaders,
        JsonNode responseBody,
        JsonNode assertions,
        boolean success,
        long durationMs,
        String errorMessage,
        LocalDateTime createdAt
) {

    public static TestReportStepResponse from(
            TestReportStep step,
            ObjectMapper objectMapper
    ) {
        return new TestReportStepResponse(
                step.id(),
                step.stepIndex(),
                step.stepName(),
                step.httpMethod(),
                step.requestUrl(),
                parse(objectMapper, step.requestHeadersJson()),
                parse(objectMapper, step.requestBodyRedacted()),
                step.responseStatus(),
                parse(objectMapper, step.responseHeadersJson()),
                parse(objectMapper, step.responseBodyRedacted()),
                parse(objectMapper, step.assertionsJson()),
                step.success(),
                step.durationMs(),
                step.errorMessage(),
                step.createdAt()
        );
    }

    private static JsonNode parse(ObjectMapper objectMapper, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("测试报告步骤 JSON 数据损坏", exception);
        }
    }
}
