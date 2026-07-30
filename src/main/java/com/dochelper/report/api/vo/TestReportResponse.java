package com.dochelper.report.api.vo;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dochelper.report.domain.TestReport;

/**
 * 测试报告详情响应。
 */
public record TestReportResponse(
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
        JsonNode metrics,
        List<TestReportStepResponse> steps,
        LocalDateTime createdAt
) {

    public static TestReportResponse from(
            TestReport report,
            List<TestReportStepResponse> steps,
            ObjectMapper objectMapper
    ) {
        return new TestReportResponse(
                report.id(),
                report.projectId(),
                report.taskId(),
                report.executionId(),
                report.title(),
                report.status(),
                report.summary(),
                report.totalSteps(),
                report.passedSteps(),
                report.failedSteps(),
                report.totalToolCalls(),
                report.durationMs(),
                report.evidenceCitations(),
                parseMetrics(report.metricsJson(), objectMapper),
                steps,
                report.createdAt()
        );
    }

    private static JsonNode parseMetrics(String value, ObjectMapper objectMapper) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("测试报告指标 JSON 数据损坏", exception);
        }
    }
}
