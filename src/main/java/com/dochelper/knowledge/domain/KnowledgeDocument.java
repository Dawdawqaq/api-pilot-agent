package com.dochelper.knowledge.domain;

import java.time.LocalDateTime;

/**
 * 知识库文档。
 */
public record KnowledgeDocument(
        Long id,
        Long projectId,
        String fileName,
        String objectKey,
        String contentType,
        long fileSize,
        String contentHash,
        String title,
        DocumentStatus status,
        String errorMessage,
        int chunkCount,
        LocalDateTime createdAt,
        LocalDateTime indexedAt,
        LocalDateTime updatedAt
) {
}
