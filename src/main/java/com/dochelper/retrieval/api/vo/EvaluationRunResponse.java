package com.dochelper.retrieval.api.vo;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dochelper.common.json.JsonValueReader;
import com.dochelper.retrieval.domain.EvaluationRun;

/**
 * Recall@K 评测运行响应。
 */
public record EvaluationRunResponse(
        Long id,
        int topK,
        int caseCount,
        int hitCount,
        double recallAtK,
        JsonNode details,
        LocalDateTime createdAt
) {

    public static EvaluationRunResponse from(EvaluationRun value, ObjectMapper objectMapper) {
        return new EvaluationRunResponse(
                value.id(),
                value.topK(),
                value.caseCount(),
                value.hitCount(),
                value.recallAtK(),
                new JsonValueReader(objectMapper).read(value.detailsJson()),
                value.createdAt()
        );
    }
}
