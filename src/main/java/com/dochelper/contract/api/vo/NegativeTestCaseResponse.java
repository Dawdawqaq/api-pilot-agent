package com.dochelper.contract.api.vo;

/**
 * 确定性负向用例建议。
 */
public record NegativeTestCaseResponse(
        String caseType,
        String target,
        String mutation,
        String expectedOutcome
) {
}
