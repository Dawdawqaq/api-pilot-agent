package com.dochelper.agent.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 危险操作确认请求。
 *
 * @param approved 是否批准
 * @param note 决策备注
 * @param planHash 用户实际审阅的计划版本摘要
 */
public record ConfirmationDecisionRequest(
        @NotNull Boolean approved,
        @Size(max = 500) String note,
        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Pattern(regexp = "[a-f0-9]{64}") String planHash
) {
}
