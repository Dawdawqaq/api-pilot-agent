package com.dochelper.report.domain;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 持久化测试报告。
 */
public record TestReport(
        Long id,
        Long projectId,
        Long taskId,
        Long executionId,
        String title,
        String status,
        String summary,
        int totalSteps,
        int passedSteps,
        int failedSteps,
        int totalToolCalls,
        long durationMs,
        List<String> evidenceCitations,
        String metricsJson,
        LocalDateTime createdAt
) {
}
