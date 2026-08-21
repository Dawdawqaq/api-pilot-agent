package com.dochelper.openapi.domain.repository;

import java.util.List;
import java.util.Optional;

import com.dochelper.openapi.domain.ApiEndpoint;
import com.dochelper.openapi.domain.OpenApiImport;
import com.dochelper.openapi.domain.ParsedOpenApiDocument;

/**
 * OpenAPI 导入及接口目录仓储。
 */
public interface OpenApiCatalogRepository {

    OpenApiImport createAttempt(OpenApiImport attempt);

    Optional<OpenApiImport> findByIdAndProjectId(Long importId, Long projectId);

    Optional<OpenApiImport> findSuccessfulByHash(Long projectId, String contentHash);

    List<OpenApiImport> findImports(Long projectId);

    int nextRevision(Long projectId);

    void completeSuccess(Long importId, Long projectId, ParsedOpenApiDocument document);

    void completeFailure(Long importId, String errorMessage);

    List<ApiEndpoint> findEndpoints(Long projectId, Long importId);

    Optional<ApiEndpoint> findEndpoint(Long projectId, Long endpointId);

    Optional<String> findSchemaJson(Long projectId, Long importId, String schemaName);
}
