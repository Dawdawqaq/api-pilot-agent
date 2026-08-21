package com.dochelper.executor.domain;

/**
 * 执行步骤在 OpenAPI 目录中解析出的确定操作。
 */
public record ResolvedOperation(
        Long endpointId,
        String operationId,
        String method,
        String pathTemplate,
        OperationRisk risk
) {
}
