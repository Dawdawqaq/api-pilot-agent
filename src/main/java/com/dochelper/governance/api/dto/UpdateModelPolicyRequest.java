package com.dochelper.governance.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新项目模型数据策略请求。
 */
public record UpdateModelPolicyRequest(
        boolean externalModelAllowed,
        @NotBlank @Size(max = 64) String allowedProvider,
        boolean allowDocumentContent,
        boolean allowSchemaContent,
        @Min(2000) @Max(200000) int promptCharacterBudget,
        @Min(1) @Max(50) int endpointTopK
) {
}
