package com.dochelper.openapi.infrastructure.persistence.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dochelper.openapi.domain.ApiEndpoint;
import com.dochelper.openapi.domain.ImportStatus;
import com.dochelper.openapi.domain.OpenApiImport;
import com.dochelper.openapi.domain.ParsedOpenApiDocument;
import com.dochelper.openapi.domain.ParsedParameter;
import com.dochelper.openapi.domain.repository.OpenApiCatalogRepository;
import com.dochelper.openapi.infrastructure.persistence.entity.ApiEndpointEntity;
import com.dochelper.openapi.infrastructure.persistence.entity.ApiParameterEntity;
import com.dochelper.openapi.infrastructure.persistence.entity.ApiSchemaEntity;
import com.dochelper.openapi.infrastructure.persistence.entity.ApiSecuritySchemeEntity;
import com.dochelper.openapi.infrastructure.persistence.entity.OpenApiImportEntity;
import com.dochelper.openapi.infrastructure.persistence.mapper.ApiEndpointMapper;
import com.dochelper.openapi.infrastructure.persistence.mapper.ApiParameterMapper;
import com.dochelper.openapi.infrastructure.persistence.mapper.ApiSchemaMapper;
import com.dochelper.openapi.infrastructure.persistence.mapper.ApiSecuritySchemeMapper;
import com.dochelper.openapi.infrastructure.persistence.mapper.OpenApiImportMapper;
import com.dochelper.project.domain.ApiProject;
import com.dochelper.project.domain.repository.ApiProjectRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于 MyBatis-Plus 的 OpenAPI 目录仓储实现。
 */
@Repository
public class MybatisOpenApiCatalogRepository implements OpenApiCatalogRepository {

    private final OpenApiImportMapper importMapper;
    private final ApiEndpointMapper endpointMapper;
    private final ApiParameterMapper parameterMapper;
    private final ApiSchemaMapper schemaMapper;
    private final ApiSecuritySchemeMapper securityMapper;
    private final ApiProjectRepository projectRepository;

    public MybatisOpenApiCatalogRepository(
            OpenApiImportMapper importMapper,
            ApiEndpointMapper endpointMapper,
            ApiParameterMapper parameterMapper,
            ApiSchemaMapper schemaMapper,
            ApiSecuritySchemeMapper securityMapper,
            ApiProjectRepository projectRepository
    ) {
        this.importMapper = importMapper;
        this.endpointMapper = endpointMapper;
        this.parameterMapper = parameterMapper;
        this.schemaMapper = schemaMapper;
        this.securityMapper = securityMapper;
        this.projectRepository = projectRepository;
    }

    @Override
    @Transactional
    public OpenApiImport createAttempt(OpenApiImport attempt) {
        projectRepository.lockById(attempt.projectId());
        OpenApiImportEntity entity = toEntity(attempt);
        entity.setRevisionNumber(nextRevision(attempt.projectId()));
        importMapper.insert(entity);
        return findByIdAndProjectId(entity.getId(), attempt.projectId()).orElseThrow();
    }

    @Override
    public Optional<OpenApiImport> findByIdAndProjectId(Long importId, Long projectId) {
        return Optional.ofNullable(importMapper.selectOne(
                Wrappers.<OpenApiImportEntity>lambdaQuery()
                        .eq(OpenApiImportEntity::getId, importId)
                        .eq(OpenApiImportEntity::getProjectId, projectId)
                        .last("LIMIT 1")
        )).map(this::toDomain);
    }

    @Override
    public Optional<OpenApiImport> findSuccessfulByHash(Long projectId, String contentHash) {
        return Optional.ofNullable(importMapper.selectOne(
                Wrappers.<OpenApiImportEntity>lambdaQuery()
                        .eq(OpenApiImportEntity::getProjectId, projectId)
                        .eq(OpenApiImportEntity::getContentHash, contentHash)
                        .eq(OpenApiImportEntity::getStatus, ImportStatus.SUCCEEDED.name())
                        .orderByDesc(OpenApiImportEntity::getRevisionNumber)
                        .last("LIMIT 1")
        )).map(this::toDomain);
    }

    @Override
    public List<OpenApiImport> findImports(Long projectId) {
        return importMapper.selectList(
                Wrappers.<OpenApiImportEntity>lambdaQuery()
                        .eq(OpenApiImportEntity::getProjectId, projectId)
                        .orderByDesc(OpenApiImportEntity::getRevisionNumber)
        ).stream().map(this::toDomain).toList();
    }

    @Override
    public int nextRevision(Long projectId) {
        return importMapper.selectMaxRevision(projectId) + 1;
    }

    @Override
    @Transactional
    public void completeSuccess(Long importId, Long projectId, ParsedOpenApiDocument document) {
        document.endpoints().forEach(endpoint -> {
            ApiEndpointEntity endpointEntity = new ApiEndpointEntity();
            endpointEntity.setProjectId(projectId);
            endpointEntity.setImportId(importId);
            endpointEntity.setPath(endpoint.path());
            endpointEntity.setHttpMethod(endpoint.httpMethod());
            endpointEntity.setOperationId(endpoint.operationId());
            endpointEntity.setSummary(endpoint.summary());
            endpointEntity.setDescription(endpoint.description());
            endpointEntity.setTagsJson(endpoint.tagsJson());
            endpointEntity.setDeprecated(endpoint.deprecated());
            endpointEntity.setRequestBodyJson(endpoint.requestBodyJson());
            endpointEntity.setResponsesJson(endpoint.responsesJson());
            endpointEntity.setSecurityJson(endpoint.securityJson());
            endpointMapper.insert(endpointEntity);

            endpoint.parameters().forEach(parameter -> {
                ApiParameterEntity parameterEntity = new ApiParameterEntity();
                parameterEntity.setEndpointId(endpointEntity.getId());
                parameterEntity.setParameterName(parameter.name());
                parameterEntity.setLocation(parameter.location());
                parameterEntity.setRequired(parameter.required());
                parameterEntity.setDescription(parameter.description());
                parameterEntity.setSchemaJson(parameter.schemaJson());
                parameterMapper.insert(parameterEntity);
            });
        });

        document.schemas().forEach(schema -> {
            ApiSchemaEntity entity = new ApiSchemaEntity();
            entity.setProjectId(projectId);
            entity.setImportId(importId);
            entity.setSchemaName(schema.name());
            entity.setSchemaJson(schema.schemaJson());
            schemaMapper.insert(entity);
        });

        document.securitySchemes().forEach(scheme -> {
            ApiSecuritySchemeEntity entity = new ApiSecuritySchemeEntity();
            entity.setProjectId(projectId);
            entity.setImportId(importId);
            entity.setSchemeName(scheme.name());
            entity.setSchemeType(scheme.type());
            entity.setHttpScheme(scheme.httpScheme());
            entity.setBearerFormat(scheme.bearerFormat());
            entity.setParameterName(scheme.parameterName());
            entity.setParameterLocation(scheme.parameterLocation());
            entity.setOpenidConnectUrl(scheme.openidConnectUrl());
            entity.setFlowsJson(scheme.flowsJson());
            securityMapper.insert(entity);
        });

        OpenApiImportEntity completed = new OpenApiImportEntity();
        completed.setId(importId);
        completed.setSpecificationVersion(document.specificationVersion());
        completed.setDocumentTitle(document.title());
        completed.setDocumentVersion(document.documentVersion());
        completed.setStatus(ImportStatus.SUCCEEDED.name());
        completed.setEndpointCount(document.endpoints().size());
        completed.setSchemaCount(document.schemas().size());
        completed.setSecuritySchemeCount(document.securitySchemes().size());
        completed.setCompletedAt(LocalDateTime.now());
        importMapper.updateById(completed);
        projectRepository.updateCurrentImport(projectId, importId);
    }

    @Override
    @Transactional
    public void completeFailure(Long importId, String errorMessage) {
        OpenApiImportEntity failed = new OpenApiImportEntity();
        failed.setId(importId);
        failed.setStatus(ImportStatus.FAILED.name());
        failed.setErrorMessage(errorMessage);
        failed.setCompletedAt(LocalDateTime.now());
        importMapper.updateById(failed);
    }

    @Override
    public List<ApiEndpoint> findEndpoints(Long projectId, Long importId) {
        Long effectiveImportId = importId;
        if (effectiveImportId == null) {
            effectiveImportId = projectRepository.findById(projectId)
                    .map(ApiProject::currentImportId)
                    .orElse(null);
        }
        if (effectiveImportId == null) {
            return List.of();
        }
        Long finalImportId = effectiveImportId;
        List<ApiEndpointEntity> entities = endpointMapper.selectList(
                Wrappers.<ApiEndpointEntity>lambdaQuery()
                        .eq(ApiEndpointEntity::getProjectId, projectId)
                        .eq(ApiEndpointEntity::getImportId, finalImportId)
                        .orderByAsc(ApiEndpointEntity::getPath)
                        .orderByAsc(ApiEndpointEntity::getHttpMethod)
        );
        if (entities.isEmpty()) {
            return List.of();
        }
        List<Long> endpointIds = entities.stream().map(ApiEndpointEntity::getId).toList();
        Map<Long, List<ParsedParameter>> parametersByEndpoint = parameterMapper.selectList(
                Wrappers.<ApiParameterEntity>lambdaQuery()
                        .in(ApiParameterEntity::getEndpointId, endpointIds)
                        .orderByAsc(ApiParameterEntity::getId)
        ).stream().collect(Collectors.groupingBy(
                ApiParameterEntity::getEndpointId,
                Collectors.mapping(this::toParameter, Collectors.toList())
        ));
        return entities.stream()
                .map(entity -> toDomain(
                        entity,
                        parametersByEndpoint.getOrDefault(entity.getId(), List.of())
                ))
                .toList();
    }

    @Override
    public Optional<ApiEndpoint> findEndpoint(Long projectId, Long endpointId) {
        ApiEndpointEntity entity = endpointMapper.selectOne(
                Wrappers.<ApiEndpointEntity>lambdaQuery()
                        .eq(ApiEndpointEntity::getProjectId, projectId)
                        .eq(ApiEndpointEntity::getId, endpointId)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public Optional<String> findSchemaJson(Long projectId, Long importId, String schemaName) {
        return Optional.ofNullable(schemaMapper.selectOne(
                Wrappers.<ApiSchemaEntity>lambdaQuery()
                        .eq(ApiSchemaEntity::getProjectId, projectId)
                        .eq(ApiSchemaEntity::getImportId, importId)
                        .eq(ApiSchemaEntity::getSchemaName, schemaName)
                        .last("LIMIT 1")
        )).map(ApiSchemaEntity::getSchemaJson);
    }

    private ApiEndpoint toDomain(ApiEndpointEntity entity) {
        List<ParsedParameter> parameters = parameterMapper.selectList(
                Wrappers.<ApiParameterEntity>lambdaQuery()
                        .eq(ApiParameterEntity::getEndpointId, entity.getId())
                        .orderByAsc(ApiParameterEntity::getId)
        ).stream().map(this::toParameter).toList();
        return toDomain(entity, parameters);
    }

    private ApiEndpoint toDomain(ApiEndpointEntity entity, List<ParsedParameter> parameters) {
        return new ApiEndpoint(
                entity.getId(),
                entity.getProjectId(),
                entity.getImportId(),
                entity.getPath(),
                entity.getHttpMethod(),
                entity.getOperationId(),
                entity.getSummary(),
                entity.getDescription(),
                entity.getTagsJson(),
                Boolean.TRUE.equals(entity.getDeprecated()),
                entity.getRequestBodyJson(),
                entity.getResponsesJson(),
                entity.getSecurityJson(),
                parameters
        );
    }

    private ParsedParameter toParameter(ApiParameterEntity parameter) {
        return new ParsedParameter(
                parameter.getParameterName(),
                parameter.getLocation(),
                Boolean.TRUE.equals(parameter.getRequired()),
                parameter.getDescription(),
                parameter.getSchemaJson()
        );
    }

    private OpenApiImportEntity toEntity(OpenApiImport value) {
        OpenApiImportEntity entity = new OpenApiImportEntity();
        entity.setId(value.id());
        entity.setProjectId(value.projectId());
        entity.setRevisionNumber(value.revisionNumber());
        entity.setRetryOfId(value.retryOfId());
        entity.setFileName(value.fileName());
        entity.setContentType(value.contentType());
        entity.setContentHash(value.contentHash());
        entity.setRawContent(value.rawContent());
        entity.setSpecificationVersion(value.specificationVersion());
        entity.setDocumentTitle(value.documentTitle());
        entity.setDocumentVersion(value.documentVersion());
        entity.setStatus(value.status().name());
        entity.setErrorMessage(value.errorMessage());
        entity.setEndpointCount(value.endpointCount());
        entity.setSchemaCount(value.schemaCount());
        entity.setSecuritySchemeCount(value.securitySchemeCount());
        return entity;
    }

    private OpenApiImport toDomain(OpenApiImportEntity entity) {
        return new OpenApiImport(
                entity.getId(),
                entity.getProjectId(),
                entity.getRevisionNumber(),
                entity.getRetryOfId(),
                entity.getFileName(),
                entity.getContentType(),
                entity.getContentHash(),
                entity.getRawContent(),
                entity.getSpecificationVersion(),
                entity.getDocumentTitle(),
                entity.getDocumentVersion(),
                ImportStatus.valueOf(entity.getStatus()),
                entity.getErrorMessage(),
                entity.getEndpointCount(),
                entity.getSchemaCount(),
                entity.getSecuritySchemeCount(),
                entity.getCreatedAt(),
                entity.getCompletedAt()
        );
    }
}
