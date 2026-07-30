package com.dochelper.retrieval.domain.repository;

import java.util.List;
import java.util.Optional;

import com.dochelper.retrieval.domain.EvaluationCase;
import com.dochelper.retrieval.domain.EvaluationRun;

/**
 * 检索评测仓储。
 */
public interface EvaluationRepository {

    EvaluationCase createCase(EvaluationCase evaluationCase);

    Optional<EvaluationCase> findCaseByName(Long projectId, String name);

    List<EvaluationCase> findCases(Long projectId);

    EvaluationRun createRun(EvaluationRun run);

    List<EvaluationRun> findRuns(Long projectId);
}
