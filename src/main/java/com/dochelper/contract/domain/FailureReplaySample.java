package com.dochelper.contract.domain;

import java.time.LocalDateTime;

/**
 * 失败请求的脱敏回放样本。
 */
public record FailureReplaySample(
        Long id,
        Long projectId,
        Long executionId,
        int stepIndex,
        String requestFingerprint,
        String requestJsonRedacted,
        String requestSecretRef,
        String errorSummary,
        LocalDateTime createdAt
) {
}
