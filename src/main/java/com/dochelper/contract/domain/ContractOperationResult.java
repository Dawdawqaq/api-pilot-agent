package com.dochelper.contract.domain;

import java.time.LocalDateTime;

/**
 * 持久化的契约校验与覆盖明细。
 */
public record ContractOperationResult(
        Long id,
        Long projectId,
        Long executionId,
        int stepIndex,
        Long endpointId,
        String operationId,
        String httpMethod,
        String pathTemplate,
        int responseStatus,
        int contractRulesTotal,
        int contractRulesCovered,
        String violationsJson,
        LocalDateTime createdAt
) {
}
