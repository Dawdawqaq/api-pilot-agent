package com.dochelper.knowledge.domain;

/**
 * 知识库文本切片。
 */
public record KnowledgeChunk(
        Long id,
        Long projectId,
        Long documentId,
        int chunkIndex,
        String sectionTitle,
        String content,
        int charCount,
        String contentHash,
        String vectorId
) {
}
