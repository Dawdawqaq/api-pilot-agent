package com.dochelper.contract.domain;

/**
 * 确定性 OpenAPI 契约违规。
 */
public record ContractViolation(String jsonPath, String rule, String message) {
}
