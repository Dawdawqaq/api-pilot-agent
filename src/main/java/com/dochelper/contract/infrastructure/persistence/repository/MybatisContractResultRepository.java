package com.dochelper.contract.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dochelper.contract.domain.ContractOperationResult;
import com.dochelper.contract.domain.FailureReplaySample;
import com.dochelper.contract.domain.TestRunCoverage;
import com.dochelper.contract.domain.repository.ContractResultRepository;
import com.dochelper.contract.infrastructure.persistence.entity.ContractOperationResultEntity;
import com.dochelper.contract.infrastructure.persistence.entity.FailureReplaySampleEntity;
import com.dochelper.contract.infrastructure.persistence.entity.TestRunCoverageEntity;
import com.dochelper.contract.infrastructure.persistence.mapper.ContractOperationResultMapper;
import com.dochelper.contract.infrastructure.persistence.mapper.FailureReplaySampleMapper;
import com.dochelper.contract.infrastructure.persistence.mapper.TestRunCoverageMapper;
import org.springframework.stereotype.Repository;

/**
 * 契约结果与回放样本的 MyBatis 仓储。
 */
@Repository
public class MybatisContractResultRepository implements ContractResultRepository {

    private final ContractOperationResultMapper operationMapper;
    private final FailureReplaySampleMapper replayMapper;
    private final TestRunCoverageMapper coverageMapper;

    public MybatisContractResultRepository(
            ContractOperationResultMapper operationMapper,
            FailureReplaySampleMapper replayMapper,
            TestRunCoverageMapper coverageMapper
    ) {
        this.operationMapper = operationMapper;
        this.replayMapper = replayMapper;
        this.coverageMapper = coverageMapper;
    }

    @Override
    public void saveOperationResult(ContractOperationResult result) {
        operationMapper.insert(toEntity(result));
    }

    @Override
    public List<ContractOperationResult> findByExecutionId(Long executionId) {
        return operationMapper.selectList(
                        Wrappers.<ContractOperationResultEntity>lambdaQuery()
                                .eq(ContractOperationResultEntity::getExecutionId, executionId)
                                .orderByAsc(ContractOperationResultEntity::getStepIndex))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public FailureReplaySample saveReplay(FailureReplaySample sample) {
        FailureReplaySampleEntity entity = toEntity(sample);
        replayMapper.insert(entity);
        return toDomain(entity);
    }

    @Override
    public Optional<FailureReplaySample> findReplay(Long projectId, Long replayId) {
        return Optional.ofNullable(replayMapper.selectOne(
                Wrappers.<FailureReplaySampleEntity>lambdaQuery()
                        .eq(FailureReplaySampleEntity::getProjectId, projectId)
                        .eq(FailureReplaySampleEntity::getId, replayId)
                        .last("LIMIT 1")
        )).map(this::toDomain);
    }

    @Override
    public void saveCoverage(TestRunCoverage value) {
        TestRunCoverageEntity entity = new TestRunCoverageEntity();
        entity.setId(value.id());
        entity.setProjectId(value.projectId());
        entity.setReportId(value.reportId());
        entity.setExecutionId(value.executionId());
        entity.setOperationTotal(value.operationTotal());
        entity.setOperationCovered(value.operationCovered());
        entity.setMethodTotal(value.methodTotal());
        entity.setMethodCovered(value.methodCovered());
        entity.setDocumentedStatusTotal(value.documentedStatusTotal());
        entity.setStatusCovered(value.statusCovered());
        entity.setSchemaRulesTotal(value.schemaRulesTotal());
        entity.setSchemaRulesCovered(value.schemaRulesCovered());
        entity.setUniqueServerErrorCount(value.uniqueServerErrorCount());
        entity.setCreatedAt(value.createdAt());
        coverageMapper.insert(entity);
    }

    private ContractOperationResultEntity toEntity(ContractOperationResult value) {
        ContractOperationResultEntity entity = new ContractOperationResultEntity();
        entity.setId(value.id());
        entity.setProjectId(value.projectId());
        entity.setExecutionId(value.executionId());
        entity.setStepIndex(value.stepIndex());
        entity.setEndpointId(value.endpointId());
        entity.setOperationId(value.operationId());
        entity.setHttpMethod(value.httpMethod());
        entity.setPathTemplate(value.pathTemplate());
        entity.setResponseStatus(value.responseStatus());
        entity.setContractRulesTotal(value.contractRulesTotal());
        entity.setContractRulesCovered(value.contractRulesCovered());
        entity.setViolationsJson(value.violationsJson());
        entity.setCreatedAt(value.createdAt());
        return entity;
    }

    private ContractOperationResult toDomain(ContractOperationResultEntity entity) {
        return new ContractOperationResult(
                entity.getId(), entity.getProjectId(), entity.getExecutionId(), entity.getStepIndex(),
                entity.getEndpointId(), entity.getOperationId(), entity.getHttpMethod(),
                entity.getPathTemplate(), entity.getResponseStatus(), entity.getContractRulesTotal(),
                entity.getContractRulesCovered(), entity.getViolationsJson(), entity.getCreatedAt()
        );
    }

    private FailureReplaySampleEntity toEntity(FailureReplaySample value) {
        FailureReplaySampleEntity entity = new FailureReplaySampleEntity();
        entity.setId(value.id());
        entity.setProjectId(value.projectId());
        entity.setExecutionId(value.executionId());
        entity.setStepIndex(value.stepIndex());
        entity.setRequestFingerprint(value.requestFingerprint());
        entity.setRequestJsonRedacted(value.requestJsonRedacted());
        entity.setRequestSecretRef(value.requestSecretRef());
        entity.setErrorSummary(value.errorSummary());
        entity.setCreatedAt(value.createdAt());
        return entity;
    }

    private FailureReplaySample toDomain(FailureReplaySampleEntity entity) {
        return new FailureReplaySample(
                entity.getId(), entity.getProjectId(), entity.getExecutionId(), entity.getStepIndex(),
                entity.getRequestFingerprint(), entity.getRequestJsonRedacted(),
                entity.getRequestSecretRef(), entity.getErrorSummary(), entity.getCreatedAt()
        );
    }
}
