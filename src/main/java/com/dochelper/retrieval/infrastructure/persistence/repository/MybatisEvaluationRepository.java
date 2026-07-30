package com.dochelper.retrieval.infrastructure.persistence.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dochelper.retrieval.domain.EvaluationCase;
import com.dochelper.retrieval.domain.EvaluationRun;
import com.dochelper.retrieval.domain.repository.EvaluationRepository;
import com.dochelper.retrieval.infrastructure.persistence.entity.EvaluationCaseEntity;
import com.dochelper.retrieval.infrastructure.persistence.entity.EvaluationRunEntity;
import com.dochelper.retrieval.infrastructure.persistence.mapper.EvaluationCaseMapper;
import com.dochelper.retrieval.infrastructure.persistence.mapper.EvaluationRunMapper;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 的检索评测仓储。
 */
@Repository
public class MybatisEvaluationRepository implements EvaluationRepository {

    private final EvaluationCaseMapper caseMapper;
    private final EvaluationRunMapper runMapper;

    public MybatisEvaluationRepository(EvaluationCaseMapper caseMapper, EvaluationRunMapper runMapper) {
        this.caseMapper = caseMapper;
        this.runMapper = runMapper;
    }

    @Override
    public EvaluationCase createCase(EvaluationCase value) {
        EvaluationCaseEntity entity = new EvaluationCaseEntity();
        entity.setId(value.id());
        entity.setProjectId(value.projectId());
        entity.setCaseName(value.name());
        entity.setQueryText(value.query());
        entity.setExpectedDocumentId(value.expectedDocumentId());
        caseMapper.insert(entity);
        return findCaseByName(value.projectId(), value.name()).orElseThrow();
    }

    @Override
    public Optional<EvaluationCase> findCaseByName(Long projectId, String name) {
        return Optional.ofNullable(caseMapper.selectOne(
                Wrappers.<EvaluationCaseEntity>lambdaQuery()
                        .eq(EvaluationCaseEntity::getProjectId, projectId)
                        .eq(EvaluationCaseEntity::getCaseName, name)
                        .last("LIMIT 1")
        )).map(this::toDomain);
    }

    @Override
    public List<EvaluationCase> findCases(Long projectId) {
        return caseMapper.selectList(
                Wrappers.<EvaluationCaseEntity>lambdaQuery()
                        .eq(EvaluationCaseEntity::getProjectId, projectId)
                        .orderByAsc(EvaluationCaseEntity::getCreatedAt)
        ).stream().map(this::toDomain).toList();
    }

    @Override
    public EvaluationRun createRun(EvaluationRun value) {
        EvaluationRunEntity entity = new EvaluationRunEntity();
        entity.setId(value.id());
        entity.setProjectId(value.projectId());
        entity.setTopK(value.topK());
        entity.setCaseCount(value.caseCount());
        entity.setHitCount(value.hitCount());
        entity.setRecallAtK(BigDecimal.valueOf(value.recallAtK()));
        entity.setDetailsJson(value.detailsJson());
        runMapper.insert(entity);
        return toDomain(runMapper.selectById(entity.getId()));
    }

    @Override
    public List<EvaluationRun> findRuns(Long projectId) {
        return runMapper.selectList(
                Wrappers.<EvaluationRunEntity>lambdaQuery()
                        .eq(EvaluationRunEntity::getProjectId, projectId)
                        .orderByDesc(EvaluationRunEntity::getCreatedAt)
        ).stream().map(this::toDomain).toList();
    }

    private EvaluationCase toDomain(EvaluationCaseEntity entity) {
        return new EvaluationCase(
                entity.getId(),
                entity.getProjectId(),
                entity.getCaseName(),
                entity.getQueryText(),
                entity.getExpectedDocumentId(),
                entity.getCreatedAt()
        );
    }

    private EvaluationRun toDomain(EvaluationRunEntity entity) {
        return new EvaluationRun(
                entity.getId(),
                entity.getProjectId(),
                entity.getTopK(),
                entity.getCaseCount(),
                entity.getHitCount(),
                entity.getRecallAtK().doubleValue(),
                entity.getDetailsJson(),
                entity.getCreatedAt()
        );
    }
}
