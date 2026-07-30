package com.dochelper.openapi.api.vo;

import java.time.LocalDateTime;

import com.dochelper.openapi.domain.ImportStatus;
import com.dochelper.openapi.domain.OpenApiImport;

/**
 * OpenAPI 导入响应。
 */
public record OpenApiImportResponse(
        Long id,
        Long projectId,
        int revisionNumber,
        Long retryOfId,
        String fileName,
        String contentHash,
        String specificationVersion,
        String documentTitle,
        String documentVersion,
        ImportStatus status,
        String errorMessage,
        int endpointCount,
        int schemaCount,
        int securitySchemeCount,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {

    public static OpenApiImportResponse from(OpenApiImport value) {
        return new OpenApiImportResponse(
                value.id(),
                value.projectId(),
                value.revisionNumber(),
                value.retryOfId(),
                value.fileName(),
                value.contentHash(),
                value.specificationVersion(),
                value.documentTitle(),
                value.documentVersion(),
                value.status(),
                value.errorMessage(),
                value.endpointCount(),
                value.schemaCount(),
                value.securitySchemeCount(),
                value.createdAt(),
                value.completedAt()
        );
    }
}
