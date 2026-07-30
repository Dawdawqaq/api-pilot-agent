package com.dochelper.agent.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 危险操作确认请求。
 *
 * @param approved 是否批准
 * @param note 决策备注
 */
public record ConfirmationDecisionRequest(
        @NotNull Boolean approved,
        @Size(max = 500) String note
) {
}
