package com.dochelper.evaluation.domain;

import java.time.LocalDateTime;

/**
 * 持久化质量与安全评测结果。
 */
public record QualityEvaluationRun(
        Long id,
        String datasetVersion,
        int serviceCount,
        int evaluationCaseCount,
        int securityCaseCount,
        int passedCaseCount,
        int blockedAttackCount,
        double taskSuccessRate,
        double validPlanRate,
        double securityBlockRate,
        long p95TaskDurationMs,
        long totalModelTokens,
        String metricsJson,
        LocalDateTime createdAt
) {
}
