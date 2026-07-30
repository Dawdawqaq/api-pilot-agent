package com.dochelper.retrieval.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建检索评测用例请求。
 */
public record CreateEvaluationCaseRequest(
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Size(max = 1000) String query,
        @NotNull Long expectedDocumentId
) {
}
