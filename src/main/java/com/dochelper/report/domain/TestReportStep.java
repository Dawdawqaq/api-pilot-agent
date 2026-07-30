package com.dochelper.report.domain;

import java.time.LocalDateTime;

/**
 * 测试报告中的脱敏执行步骤快照。
 */
public record TestReportStep(
        Long id,
        Long reportId,
        Long executionStepId,
        int stepIndex,
        String stepName,
        String httpMethod,
        String requestUrl,
        String requestHeadersJson,
        String requestBodyRedacted,
        Integer responseStatus,
        String responseHeadersJson,
        String responseBodyRedacted,
        String assertionsJson,
        boolean success,
        long durationMs,
        String errorMessage,
        LocalDateTime createdAt
) {
}
