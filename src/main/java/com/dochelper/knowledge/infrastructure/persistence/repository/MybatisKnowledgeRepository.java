package com.dochelper.knowledge.infrastructure.persistence.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dochelper.knowledge.domain.DocumentStatus;
import com.dochelper.knowledge.domain.KnowledgeChunk;
import com.dochelper.knowledge.domain.KnowledgeDocument;
import com.dochelper.knowledge.domain.repository.KnowledgeRepository;
import com.dochelper.knowledge.infrastructure.persistence.entity.KnowledgeChunkEntity;
import com.dochelper.knowledge.infrastructure.persistence.entity.KnowledgeDocumentEntity;
import com.dochelper.knowledge.infrastructure.persistence.mapper.KnowledgeChunkMapper;
import com.dochelper.knowledge.infrastructure.persistence.mapper.KnowledgeDocumentMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于 MyBatis-Plus 的知识库仓储实现。
 */
@Repository
public class MybatisKnowledgeRepository implements KnowledgeRepository {

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;

    public MybatisKnowledgeRepository(
            KnowledgeDocumentMapper documentMapper,
            KnowledgeChunkMapper chunkMapper
    ) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
    }

    @Override
    public KnowledgeDocument createDocument(KnowledgeDocument document) {
        documentMapper.insert(toEntity(document));
        return findDocument(document.projectId(), document.id()).orElseThrow();
    }

    @Override
    public Optional<KnowledgeDocument> findDocument(Long projectId, Long documentId) {
        return Optional.ofNullable(documentMapper.selectOne(
                Wrappers.<KnowledgeDocumentEntity>lambdaQuery()
                        .eq(KnowledgeDocumentEntity::getId, documentId)
                        .eq(KnowledgeDocumentEntity::getProjectId, projectId)
                        .last("LIMIT 1")
        )).map(this::toDomain);
    }

    @Override
    public Optional<KnowledgeDocument> findIndexedByHash(Long projectId, String contentHash) {
        return Optional.ofNullable(documentMapper.selectOne(
                Wrappers.<KnowledgeDocumentEntity>lambdaQuery()
                        .eq(KnowledgeDocumentEntity::getProjectId, projectId)
                        .eq(KnowledgeDocumentEntity::getContentHash, contentHash)
                        .eq(KnowledgeDocumentEntity::getStatus, DocumentStatus.INDEXED.name())
                        .orderByDesc(KnowledgeDocumentEntity::getCreatedAt)
                        .last("LIMIT 1")
        )).map(this::toDomain);
    }

    @Override
    public List<KnowledgeDocument> findDocuments(Long projectId) {
        return documentMapper.selectList(
                Wrappers.<KnowledgeDocumentEntity>lambdaQuery()
                        .eq(KnowledgeDocumentEntity::getProjectId, projectId)
                        .orderByDesc(KnowledgeDocumentEntity::getCreatedAt)
        ).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public void replaceChunks(Long documentId, List<KnowledgeChunk> chunks) {
        chunkMapper.delete(Wrappers.<KnowledgeChunkEntity>lambdaQuery()
                .eq(KnowledgeChunkEntity::getDocumentId, documentId));
        chunks.forEach(chunk -> chunkMapper.insert(toEntity(chunk)));
    }

    @Override
    public void markIndexed(Long documentId, String title, int chunkCount) {
        KnowledgeDocumentEntity entity = new KnowledgeDocumentEntity();
        entity.setId(documentId);
        entity.setTitle(title);
        entity.setStatus(DocumentStatus.INDEXED.name());
        entity.setErrorMessage("");
        entity.setChunkCount(chunkCount);
        entity.setIndexedAt(LocalDateTime.now());
        documentMapper.updateById(entity);
    }

    @Override
    public void markFailed(Long documentId, String errorMessage) {
        KnowledgeDocumentEntity entity = new KnowledgeDocumentEntity();
        entity.setId(documentId);
        entity.setStatus(DocumentStatus.FAILED.name());
        entity.setErrorMessage(errorMessage);
        documentMapper.updateById(entity);
    }

    @Override
    public List<KnowledgeChunk> findChunks(Long documentId) {
        return chunkMapper.selectList(
                Wrappers.<KnowledgeChunkEntity>lambdaQuery()
                        .eq(KnowledgeChunkEntity::getDocumentId, documentId)
                        .orderByAsc(KnowledgeChunkEntity::getChunkIndex)
        ).stream().map(this::toDomain).toList();
    }

    @Override
    public List<KnowledgeChunk> keywordSearch(Long projectId, String term, int limit) {
        return chunkMapper.keywordSearch(projectId, term, limit).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void deleteDocument(Long documentId) {
        chunkMapper.delete(Wrappers.<KnowledgeChunkEntity>lambdaQuery()
                .eq(KnowledgeChunkEntity::getDocumentId, documentId));
        documentMapper.deleteById(documentId);
    }

    private KnowledgeDocumentEntity toEntity(KnowledgeDocument value) {
        KnowledgeDocumentEntity entity = new KnowledgeDocumentEntity();
        entity.setId(value.id());
        entity.setProjectId(value.projectId());
        entity.setFileName(value.fileName());
        entity.setObjectKey(value.objectKey());
        entity.setContentType(value.contentType());
        entity.setFileSize(value.fileSize());
        entity.setContentHash(value.contentHash());
        entity.setTitle(value.title());
        entity.setStatus(value.status().name());
        entity.setErrorMessage(value.errorMessage());
        entity.setChunkCount(value.chunkCount());
        return entity;
    }

    private KnowledgeChunkEntity toEntity(KnowledgeChunk value) {
        KnowledgeChunkEntity entity = new KnowledgeChunkEntity();
        entity.setId(value.id());
        entity.setProjectId(value.projectId());
        entity.setDocumentId(value.documentId());
        entity.setChunkIndex(value.chunkIndex());
        entity.setSectionTitle(value.sectionTitle());
        entity.setContent(value.content());
        entity.setCharCount(value.charCount());
        entity.setContentHash(value.contentHash());
        entity.setVectorId(value.vectorId());
        return entity;
    }

    private KnowledgeDocument toDomain(KnowledgeDocumentEntity entity) {
        return new KnowledgeDocument(
                entity.getId(),
                entity.getProjectId(),
                entity.getFileName(),
                entity.getObjectKey(),
                entity.getContentType(),
                entity.getFileSize(),
                entity.getContentHash(),
                entity.getTitle(),
                DocumentStatus.valueOf(entity.getStatus()),
                entity.getErrorMessage(),
                entity.getChunkCount(),
                entity.getCreatedAt(),
                entity.getIndexedAt(),
                entity.getUpdatedAt()
        );
    }

    private KnowledgeChunk toDomain(KnowledgeChunkEntity entity) {
        return new KnowledgeChunk(
                entity.getId(),
                entity.getProjectId(),
                entity.getDocumentId(),
                entity.getChunkIndex(),
                entity.getSectionTitle(),
                entity.getContent(),
                entity.getCharCount(),
                entity.getContentHash(),
                entity.getVectorId()
        );
    }
}
