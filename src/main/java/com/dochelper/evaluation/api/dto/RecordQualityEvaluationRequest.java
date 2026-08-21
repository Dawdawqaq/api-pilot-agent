package com.dochelper.evaluation.api.dto;

import java.util.Map;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 记录可重复质量评测结果的请求。
 */
public record RecordQualityEvaluationRequest(
        @NotBlank @Size(max = 64) String datasetVersion,
        @Min(1) int serviceCount,
        @Min(1) int evaluationCaseCount,
        @Min(1) int securityCaseCount,
        @Min(0) int passedCaseCount,
        @Min(0) int blockedAttackCount,
        @DecimalMin("0") @DecimalMax("1") double taskSuccessRate,
        @DecimalMin("0") @DecimalMax("1") double validPlanRate,
        @DecimalMin("0") @DecimalMax("1") double securityBlockRate,
        @Min(0) long p95TaskDurationMs,
        @Min(0) long totalModelTokens,
        @NotNull Map<String, Object> metrics
) {
}
