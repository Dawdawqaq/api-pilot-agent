package com.dochelper.contract.api.vo;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 脱敏失败回放样本响应。
 */
public record FailureReplayResponse(
        Long id,
        Long executionId,
        int stepIndex,
        String requestFingerprint,
        JsonNode request,
        String errorSummary,
        LocalDateTime createdAt
) {
}
