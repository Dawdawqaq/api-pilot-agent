package com.dochelper.retrieval.domain;

import java.time.LocalDateTime;

/**
 * Recall@K 评测运行记录。
 */
public record EvaluationRun(
        Long id,
        Long projectId,
        int topK,
        int caseCount,
        int hitCount,
        double recallAtK,
        String detailsJson,
        LocalDateTime createdAt
) {
}
