package com.dochelper.retrieval.domain;

import java.time.LocalDateTime;

/**
 * 检索评测用例。
 */
public record EvaluationCase(
        Long id,
        Long projectId,
        String name,
        String query,
        Long expectedDocumentId,
        LocalDateTime createdAt
) {
}
