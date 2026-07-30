package com.dochelper.retrieval.api.vo;

import java.time.LocalDateTime;

import com.dochelper.retrieval.domain.EvaluationCase;

/**
 * 检索评测用例响应。
 */
public record EvaluationCaseResponse(
        Long id,
        String name,
        String query,
        Long expectedDocumentId,
        LocalDateTime createdAt
) {

    public static EvaluationCaseResponse from(EvaluationCase value) {
        return new EvaluationCaseResponse(
                value.id(),
                value.name(),
                value.query(),
                value.expectedDocumentId(),
                value.createdAt()
        );
    }
}
