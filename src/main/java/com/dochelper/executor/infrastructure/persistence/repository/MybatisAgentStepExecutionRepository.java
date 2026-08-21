package com.dochelper.executor.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dochelper.executor.domain.AgentStepExecution;
import com.dochelper.executor.domain.ExecutionErrorCategory;
import com.dochelper.executor.domain.StepExecutionStatus;
import com.dochelper.executor.domain.repository.AgentStepExecutionRepository;
import com.dochelper.executor.infrastructure.persistence.entity.AgentStepExecutionEntity;
import com.dochelper.executor.infrastructure.persistence.mapper.AgentStepExecutionMapper;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 的 Agent 步骤仓储。
 */
@Repository
public class MybatisAgentStepExecutionRepository implements AgentStepExecutionRepository {

    private final AgentStepExecutionMapper mapper;

    public MybatisAgentStepExecutionRepository(AgentStepExecutionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public AgentStepExecution create(AgentStepExecution execution) {
        AgentStepExecutionEntity entity = toEntity(execution);
        mapper.insert(entity);
        return toDomain(entity);
    }

    @Override
    public void complete(AgentStepExecution execution) {
        mapper.updateById(toEntity(execution));
    }

    @Override
    public Optional<AgentStepExecution> findSucceeded(
            Long taskId,
            int stepIndex,
            String requestFingerprint
    ) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<AgentStepExecutionEntity>lambdaQuery()
                        .eq(AgentStepExecutionEntity::getTaskId, taskId)
                        .eq(AgentStepExecutionEntity::getStepIndex, stepIndex)
                        .eq(AgentStepExecutionEntity::getRequestFingerprint, requestFingerprint)
                        .eq(AgentStepExecutionEntity::getStatus, StepExecutionStatus.SUCCEEDED.name())
                        .orderByDesc(AgentStepExecutionEntity::getAttempt)
                        .last("LIMIT 1")
        )).map(this::toDomain);
    }

    @Override
    public int nextAttempt(Long taskId, int stepIndex) {
        return mapper.selectNextAttempt(taskId, stepIndex);
    }

    @Override
    public List<AgentStepExecution> findByTaskId(Long taskId) {
        return mapper.selectList(Wrappers.<AgentStepExecutionEntity>lambdaQuery()
                        .eq(AgentStepExecutionEntity::getTaskId, taskId)
                        .orderByAsc(AgentStepExecutionEntity::getStepIndex)
                        .orderByAsc(AgentStepExecutionEntity::getAttempt))
                .stream().map(this::toDomain).toList();
    }

    private AgentStepExecutionEntity toEntity(AgentStepExecution value) {
        AgentStepExecutionEntity entity = new AgentStepExecutionEntity();
        entity.setId(value.id());
        entity.setTaskId(value.taskId());
        entity.setExecutionId(value.executionId());
        entity.setStepIndex(value.stepIndex());
        entity.setAttempt(value.attempt());
        entity.setStatus(value.status().name());
        entity.setRequestFingerprint(value.requestFingerprint());
        entity.setIdempotencyKeyHash(value.idempotencyKeyHash());
        entity.setInputVariableNamesJson(value.inputVariableNamesJson());
        entity.setOutputSecretRef(value.outputSecretRef());
        entity.setResponseStatus(value.responseStatus());
        entity.setErrorCategory(value.errorCategory() == null ? null : value.errorCategory().name());
        entity.setErrorCode(value.errorCode());
        entity.setErrorMessage(value.errorMessage());
        entity.setDurationMs(value.durationMs());
        entity.setCreatedAt(value.createdAt());
        entity.setCompletedAt(value.completedAt());
        return entity;
    }

    private AgentStepExecution toDomain(AgentStepExecutionEntity entity) {
        return new AgentStepExecution(
                entity.getId(), entity.getTaskId(), entity.getExecutionId(),
                entity.getStepIndex(), entity.getAttempt(), StepExecutionStatus.valueOf(entity.getStatus()),
                entity.getRequestFingerprint(), entity.getIdempotencyKeyHash(),
                entity.getInputVariableNamesJson(), entity.getOutputSecretRef(), entity.getResponseStatus(),
                entity.getErrorCategory() == null ? null : ExecutionErrorCategory.valueOf(entity.getErrorCategory()),
                entity.getErrorCode(), entity.getErrorMessage(), entity.getDurationMs(),
                entity.getCreatedAt(), entity.getCompletedAt()
        );
    }
}
