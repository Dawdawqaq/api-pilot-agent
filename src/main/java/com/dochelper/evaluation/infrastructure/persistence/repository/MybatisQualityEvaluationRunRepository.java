package com.dochelper.evaluation.infrastructure.persistence.repository;

import java.util.List;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dochelper.evaluation.domain.QualityEvaluationRun;
import com.dochelper.evaluation.domain.repository.QualityEvaluationRunRepository;
import com.dochelper.evaluation.infrastructure.persistence.entity.QualityEvaluationRunEntity;
import com.dochelper.evaluation.infrastructure.persistence.mapper.QualityEvaluationRunMapper;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 的质量评测仓储实现。
 */
@Repository
public class MybatisQualityEvaluationRunRepository implements QualityEvaluationRunRepository {

    private final QualityEvaluationRunMapper mapper;

    public MybatisQualityEvaluationRunRepository(QualityEvaluationRunMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public QualityEvaluationRun save(QualityEvaluationRun run) {
        QualityEvaluationRunEntity entity = toEntity(run);
        mapper.insert(entity);
        return toDomain(entity);
    }

    @Override
    public List<QualityEvaluationRun> findLatest(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return mapper.selectList(Wrappers.<QualityEvaluationRunEntity>lambdaQuery()
                        .orderByDesc(QualityEvaluationRunEntity::getCreatedAt)
                        .last("LIMIT " + safeLimit))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private QualityEvaluationRunEntity toEntity(QualityEvaluationRun run) {
        QualityEvaluationRunEntity entity = new QualityEvaluationRunEntity();
        entity.setId(run.id());
        entity.setDatasetVersion(run.datasetVersion());
        entity.setServiceCount(run.serviceCount());
        entity.setEvaluationCaseCount(run.evaluationCaseCount());
        entity.setSecurityCaseCount(run.securityCaseCount());
        entity.setPassedCaseCount(run.passedCaseCount());
        entity.setBlockedAttackCount(run.blockedAttackCount());
        entity.setTaskSuccessRate(run.taskSuccessRate());
        entity.setValidPlanRate(run.validPlanRate());
        entity.setSecurityBlockRate(run.securityBlockRate());
        entity.setP95TaskDurationMs(run.p95TaskDurationMs());
        entity.setTotalModelTokens(run.totalModelTokens());
        entity.setMetricsJson(run.metricsJson());
        entity.setCreatedAt(run.createdAt());
        return entity;
    }

    private QualityEvaluationRun toDomain(QualityEvaluationRunEntity entity) {
        return new QualityEvaluationRun(
                entity.getId(), entity.getDatasetVersion(), entity.getServiceCount(),
                entity.getEvaluationCaseCount(), entity.getSecurityCaseCount(),
                entity.getPassedCaseCount(), entity.getBlockedAttackCount(),
                entity.getTaskSuccessRate(), entity.getValidPlanRate(),
                entity.getSecurityBlockRate(), entity.getP95TaskDurationMs(),
                entity.getTotalModelTokens(), entity.getMetricsJson(), entity.getCreatedAt()
        );
    }
}
