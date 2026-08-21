package com.dochelper.contract.domain;

import java.util.List;

/**
 * 单个接口响应的契约校验结果。
 */
public record ContractValidationResult(
        int totalRules,
        int coveredRules,
        List<ContractViolation> violations
) {

    public boolean passed() {
        return violations.isEmpty();
    }
}
