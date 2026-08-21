package com.dochelper.contract.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 失败样本回放请求。
 */
public record ReplayFailureRequest(@NotNull Long environmentId) {
}
