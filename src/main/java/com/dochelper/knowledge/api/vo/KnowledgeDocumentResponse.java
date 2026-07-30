package com.dochelper.knowledge.api.vo;

import java.time.LocalDateTime;

import com.dochelper.knowledge.domain.DocumentStatus;
import com.dochelper.knowledge.domain.KnowledgeDocument;

/**
 * 知识库文档响应。
 */
public record KnowledgeDocumentResponse(
        Long id,
        Long projectId,
        String fileName,
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

    public static KnowledgeDocumentResponse from(KnowledgeDocument value) {
        return new KnowledgeDocumentResponse(
                value.id(),
                value.projectId(),
                value.fileName(),
                value.contentType(),
                value.fileSize(),
                value.contentHash(),
                value.title(),
                value.status(),
                value.errorMessage(),
                value.chunkCount(),
                value.createdAt(),
                value.indexedAt(),
                value.updatedAt()
        );
    }
}
