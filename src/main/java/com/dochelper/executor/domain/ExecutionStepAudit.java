package com.dochelper.executor.domain;

import java.time.LocalDateTime;

/**
 * 单步 HTTP 调用的脱敏审计记录。
 */
public record ExecutionStepAudit(
        Long id,
        Long executionId,
        int stepIndex,
        String stepName,
        String httpMethod,
        String requestUrl,
        String requestHeadersJson,
        String requestBodyRedacted,
        Integer responseStatus,
        String responseHeadersJson,
        String responseBodyRedacted,
        String extractedNamesJson,
        String assertionsJson,
        boolean success,
        long durationMs,
        String errorMessage,
        LocalDateTime createdAt
) {
}
