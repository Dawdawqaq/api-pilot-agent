package com.dochelper.contract.domain;

import java.time.LocalDateTime;

/**
 * 单次报告的可追溯覆盖率汇总。
 */
public record TestRunCoverage(
        Long id,
        Long projectId,
        Long reportId,
        Long executionId,
        int operationTotal,
        int operationCovered,
        int methodTotal,
        int methodCovered,
        int documentedStatusTotal,
        int statusCovered,
        int schemaRulesTotal,
        int schemaRulesCovered,
        int uniqueServerErrorCount,
        LocalDateTime createdAt
) {
}
