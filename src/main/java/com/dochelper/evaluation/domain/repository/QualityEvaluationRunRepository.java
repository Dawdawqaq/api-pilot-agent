package com.dochelper.evaluation.domain.repository;

import java.util.List;

import com.dochelper.evaluation.domain.QualityEvaluationRun;

/**
 * 质量评测运行仓储。
 */
public interface QualityEvaluationRunRepository {

    QualityEvaluationRun save(QualityEvaluationRun run);

    List<QualityEvaluationRun> findLatest(int limit);
}
