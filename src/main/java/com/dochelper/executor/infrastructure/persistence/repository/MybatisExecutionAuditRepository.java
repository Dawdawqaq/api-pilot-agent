package com.dochelper.executor.infrastructure.persistence.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dochelper.executor.domain.ExecutionAudit;
import com.dochelper.executor.domain.ExecutionStatus;
import com.dochelper.executor.domain.ExecutionStepAudit;
import com.dochelper.executor.domain.repository.ExecutionAuditRepository;
import com.dochelper.executor.infrastructure.persistence.entity.ExecutionAuditEntity;
import com.dochelper.executor.infrastructure.persistence.entity.ExecutionStepAuditEntity;
import com.dochelper.executor.infrastructure.persistence.mapper.ExecutionAuditMapper;
import com.dochelper.executor.infrastructure.persistence.mapper.ExecutionStepAuditMapper;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 的场景执行审计仓储。
 */
@Repository
public class MybatisExecutionAuditRepository implements ExecutionAuditRepository {

    private final ExecutionAuditMapper executionMapper;
    private final ExecutionStepAuditMapper stepMapper;

    public MybatisExecutionAuditRepository(
            ExecutionAuditMapper executionMapper,
            ExecutionStepAuditMapper stepMapper
    ) {
        this.executionMapper = executionMapper;
        this.stepMapper = stepMapper;
    }

    @Override
    public ExecutionAudit create(ExecutionAudit audit) {
        executionMapper.insert(toEntity(audit));
        return findByProjectAndId(audit.projectId(), audit.id()).orElseThrow();
    }

    @Override
    public void saveStep(ExecutionStepAudit step) {
        stepMapper.insert(toEntity(step));
    }

    @Override
    public void complete(
            Long executionId,
            ExecutionStatus status,
            int completedStepCount,
            long durationMs,
            String errorCode,
            String errorMessage
    ) {
        ExecutionAuditEntity entity = new ExecutionAuditEntity();
        entity.setId(executionId);
        entity.setStatus(status.name());
        entity.setCompletedStepCount(completedStepCount);
        entity.setDurationMs(durationMs);
        entity.setErrorCode(errorCode);
        entity.setErrorMessage(errorMessage);
        entity.setCompletedAt(LocalDateTime.now());
        executionMapper.updateById(entity);
    }

    @Override
    public Optional<ExecutionAudit> findByProjectAndId(Long projectId, Long executionId) {
        return Optional.ofNullable(executionMapper.selectOne(
                Wrappers.<ExecutionAuditEntity>lambdaQuery()
                        .eq(ExecutionAuditEntity::getId, executionId)
                        .eq(ExecutionAuditEntity::getProjectId, projectId)
                        .last("LIMIT 1")
        )).map(this::toDomain);
    }

    @Override
    public List<ExecutionStepAudit> findSteps(Long executionId) {
        return stepMapper.selectList(
                Wrappers.<ExecutionStepAuditEntity>lambdaQuery()
                        .eq(ExecutionStepAuditEntity::getExecutionId, executionId)
                        .orderByAsc(ExecutionStepAuditEntity::getStepIndex)
        ).stream().map(this::toDomain).toList();
    }

    private ExecutionAuditEntity toEntity(ExecutionAudit value) {
        ExecutionAuditEntity entity = new ExecutionAuditEntity();
        entity.setId(value.id());
        entity.setProjectId(value.projectId());
        entity.setEnvironmentId(value.environmentId());
        entity.setStatus(value.status().name());
        entity.setStepCount(value.stepCount());
        entity.setCompletedStepCount(value.completedStepCount());
        entity.setDurationMs(value.durationMs());
        entity.setErrorCode(value.errorCode());
        entity.setErrorMessage(value.errorMessage());
        entity.setCreatedAt(value.createdAt());
        entity.setCompletedAt(value.completedAt());
        return entity;
    }

    private ExecutionStepAuditEntity toEntity(ExecutionStepAudit value) {
        ExecutionStepAuditEntity entity = new ExecutionStepAuditEntity();
        entity.setId(value.id());
        entity.setExecutionId(value.executionId());
        entity.setStepIndex(value.stepIndex());
        entity.setStepName(value.stepName());
        entity.setHttpMethod(value.httpMethod());
        entity.setRequestUrl(value.requestUrl());
        entity.setRequestHeadersJson(value.requestHeadersJson());
        entity.setRequestBodyRedacted(value.requestBodyRedacted());
        entity.setResponseStatus(value.responseStatus());
        entity.setResponseHeadersJson(value.responseHeadersJson());
        entity.setResponseBodyRedacted(value.responseBodyRedacted());
        entity.setExtractedNamesJson(value.extractedNamesJson());
        entity.setAssertionsJson(value.assertionsJson());
        entity.setSuccess(value.success());
        entity.setDurationMs(value.durationMs());
        entity.setErrorMessage(value.errorMessage());
        entity.setCreatedAt(value.createdAt());
        return entity;
    }

    private ExecutionAudit toDomain(ExecutionAuditEntity entity) {
        return new ExecutionAudit(
                entity.getId(),
                entity.getProjectId(),
                entity.getEnvironmentId(),
                ExecutionStatus.valueOf(entity.getStatus()),
                entity.getStepCount(),
                entity.getCompletedStepCount(),
                entity.getDurationMs(),
                entity.getErrorCode(),
                entity.getErrorMessage(),
                entity.getCreatedAt(),
                entity.getCompletedAt()
        );
    }

    private ExecutionStepAudit toDomain(ExecutionStepAuditEntity entity) {
        return new ExecutionStepAudit(
                entity.getId(),
                entity.getExecutionId(),
                entity.getStepIndex(),
                entity.getStepName(),
                entity.getHttpMethod(),
                entity.getRequestUrl(),
                entity.getRequestHeadersJson(),
                entity.getRequestBodyRedacted(),
                entity.getResponseStatus(),
                entity.getResponseHeadersJson(),
                entity.getResponseBodyRedacted(),
                entity.getExtractedNamesJson(),
                entity.getAssertionsJson(),
                Boolean.TRUE.equals(entity.getSuccess()),
                entity.getDurationMs(),
                entity.getErrorMessage(),
                entity.getCreatedAt()
        );
    }
}
