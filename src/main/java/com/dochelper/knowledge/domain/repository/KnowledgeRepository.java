package com.dochelper.knowledge.domain.repository;

import java.util.List;
import java.util.Optional;

import com.dochelper.knowledge.domain.KnowledgeChunk;
import com.dochelper.knowledge.domain.KnowledgeDocument;

/**
 * 知识库文档与切片仓储。
 */
public interface KnowledgeRepository {

    KnowledgeDocument createDocument(KnowledgeDocument document);

    Optional<KnowledgeDocument> findDocument(Long projectId, Long documentId);

    Optional<KnowledgeDocument> findIndexedByHash(Long projectId, String contentHash);

    List<KnowledgeDocument> findDocuments(Long projectId);

    void replaceChunks(Long documentId, List<KnowledgeChunk> chunks);

    void markIndexed(Long documentId, String title, int chunkCount);

    void markFailed(Long documentId, String errorMessage);

    List<KnowledgeChunk> findChunks(Long documentId);

    List<KnowledgeChunk> keywordSearch(Long projectId, String term, int limit);

    void deleteDocument(Long documentId);
}
