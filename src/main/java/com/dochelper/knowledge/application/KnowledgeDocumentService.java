package com.dochelper.knowledge.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.dochelper.common.exception.BusinessException;
import com.dochelper.infrastructure.storage.ObjectStorageGateway;
import com.dochelper.knowledge.config.KnowledgeProperties;
import com.dochelper.knowledge.domain.ChunkDraft;
import com.dochelper.knowledge.domain.DocumentStatus;
import com.dochelper.knowledge.domain.ExtractedDocument;
import com.dochelper.knowledge.domain.KnowledgeChunk;
import com.dochelper.knowledge.domain.KnowledgeDocument;
import com.dochelper.knowledge.domain.repository.KnowledgeRepository;
import com.dochelper.knowledge.exception.DocumentExtractionException;
import com.dochelper.knowledge.exception.KnowledgeErrorCode;
import com.dochelper.project.domain.repository.ApiProjectRepository;
import com.dochelper.project.exception.ProjectErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

/**
 * 文档对象存储、文本解析、切片和向量索引服务。
 */
@Service
public class KnowledgeDocumentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeDocumentService.class);

    private final KnowledgeRepository repository;
    private final ApiProjectRepository projectRepository;
    private final ObjectStorageGateway storageGateway;
    private final TikaDocumentExtractor extractor;
    private final SectionAwareChunker chunker;
    private final VectorStore vectorStore;
    private final KnowledgeProperties properties;

    public KnowledgeDocumentService(
            KnowledgeRepository repository,
            ApiProjectRepository projectRepository,
            ObjectStorageGateway storageGateway,
            TikaDocumentExtractor extractor,
            SectionAwareChunker chunker,
            VectorStore vectorStore,
            KnowledgeProperties properties
    ) {
        this.repository = repository;
        this.projectRepository = projectRepository;
        this.storageGateway = storageGateway;
        this.extractor = extractor;
        this.chunker = chunker;
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    public KnowledgeDocument upload(
            Long projectId,
            String fileName,
            String requestContentType,
            byte[] content
    ) {
        requireProject(projectId);
        validateFile(fileName, content);
        String hash = sha256(content);
        return repository.findIndexedByHash(projectId, hash)
                .orElseGet(() -> createAndIndex(
                        projectId,
                        safeFileName(fileName),
                        requestContentType,
                        hash,
                        content
                ));
    }

    public KnowledgeDocument retry(Long projectId, Long documentId) {
        requireProject(projectId);
        KnowledgeDocument document = requireDocument(projectId, documentId);
        if (document.status() != DocumentStatus.FAILED) {
            throw new BusinessException(KnowledgeErrorCode.DOCUMENT_NOT_RETRYABLE);
        }
        try {
            deleteVectors(repository.findChunks(documentId));
            byte[] content = storageGateway.get(document.objectKey());
            index(document, content);
            return requireDocument(projectId, documentId);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            markFailed(documentId, exception);
            throw new BusinessException(
                    KnowledgeErrorCode.INDEXING_FAILED,
                    "文档重试索引失败，documentId=" + documentId
            );
        }
    }

    public List<KnowledgeDocument> list(Long projectId) {
        requireProject(projectId);
        return repository.findDocuments(projectId);
    }

    public KnowledgeDocument get(Long projectId, Long documentId) {
        requireProject(projectId);
        return requireDocument(projectId, documentId);
    }

    public boolean delete(Long projectId, Long documentId) {
        requireProject(projectId);
        KnowledgeDocument document = requireDocument(projectId, documentId);
        try {
            deleteVectors(repository.findChunks(documentId));
            storageGateway.remove(document.objectKey());
            repository.deleteDocument(documentId);
            return true;
        } catch (Exception exception) {
            throw new BusinessException(
                    KnowledgeErrorCode.INDEXING_FAILED,
                    "文档删除失败，documentId=" + documentId
            );
        }
    }

    private KnowledgeDocument createAndIndex(
            Long projectId,
            String fileName,
            String requestContentType,
            String hash,
            byte[] content
    ) {
        Long documentId = IdWorker.getId();
        String objectKey = "projects/" + projectId + "/documents/" + documentId + "/" + fileName;
        String contentType = requestContentType == null || requestContentType.isBlank()
                ? "application/octet-stream"
                : requestContentType;
        KnowledgeDocument document = repository.createDocument(new KnowledgeDocument(
                documentId,
                projectId,
                fileName,
                objectKey,
                contentType,
                content.length,
                hash,
                null,
                DocumentStatus.PROCESSING,
                null,
                0,
                null,
                null,
                null
        ));
        try {
            storageGateway.put(objectKey, content, contentType);
            index(document, content);
            return requireDocument(projectId, documentId);
        } catch (DocumentExtractionException exception) {
            repository.markFailed(documentId, limitMessage(exception.getMessage()));
            throw new BusinessException(
                    KnowledgeErrorCode.EXTRACTION_FAILED,
                    "文档解析失败，documentId=" + documentId + "，原因：" + exception.getMessage()
            );
        } catch (Exception exception) {
            markFailed(documentId, exception);
            throw new BusinessException(
                    KnowledgeErrorCode.INDEXING_FAILED,
                    "文档索引失败，documentId=" + documentId
            );
        }
    }

    private void index(KnowledgeDocument document, byte[] content) {
        ExtractedDocument extracted = extractor.extract(content, document.fileName());
        List<ChunkDraft> drafts = chunker.chunk(extracted.title(), extracted.content());
        if (drafts.isEmpty()) {
            throw new DocumentExtractionException("文档未生成有效切片");
        }
        List<KnowledgeChunk> chunks = drafts.stream()
                .map(draft -> toChunk(document, draft))
                .toList();
        repository.replaceChunks(document.id(), chunks);
        List<Document> vectorDocuments = chunks.stream()
                .map(chunk -> toVectorDocument(document, chunk))
                .toList();
        vectorStore.add(vectorDocuments);
        repository.markIndexed(document.id(), extracted.title(), chunks.size());
    }

    private KnowledgeChunk toChunk(KnowledgeDocument document, ChunkDraft draft) {
        Long chunkId = IdWorker.getId();
        String vectorId = UUID.nameUUIDFromBytes(
                (document.projectId() + ":" + document.id() + ":" + chunkId)
                        .getBytes(StandardCharsets.UTF_8)
        ).toString();
        return new KnowledgeChunk(
                chunkId,
                document.projectId(),
                document.id(),
                draft.index(),
                draft.sectionTitle(),
                draft.content(),
                draft.content().length(),
                sha256(draft.content().getBytes(StandardCharsets.UTF_8)),
                vectorId
        );
    }

    private Document toVectorDocument(KnowledgeDocument document, KnowledgeChunk chunk) {
        String section = chunk.sectionTitle() == null
                ? document.fileName()
                : chunk.sectionTitle();
        return Document.builder()
                .id(chunk.vectorId())
                .text(section + "\n" + chunk.content())
                .metadata(Map.of(
                        "project_id", document.projectId().toString(),
                        "document_id", document.id(),
                        "chunk_id", chunk.id(),
                        "chunk_index", chunk.chunkIndex(),
                        "source_name", document.fileName(),
                        "section", section
                ))
                .build();
    }

    private void deleteVectors(List<KnowledgeChunk> chunks) {
        List<String> validVectorIds = chunks.stream()
                .map(KnowledgeChunk::vectorId)
                .filter(this::isValidUuid)
                .toList();
        if (validVectorIds.size() != chunks.size()) {
            LOGGER.warn("检测到非法向量标识，已跳过无效 Qdrant 删除请求");
        }
        if (!validVectorIds.isEmpty()) {
            vectorStore.delete(validVectorIds);
        }
    }

    private boolean isValidUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void validateFile(String fileName, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessException(KnowledgeErrorCode.EMPTY_FILE);
        }
        if (content.length > properties.maxFileSizeBytes()) {
            throw new BusinessException(KnowledgeErrorCode.FILE_TOO_LARGE);
        }
        String normalized = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (!normalized.endsWith(".pdf")
                && !normalized.endsWith(".md")
                && !normalized.endsWith(".markdown")
                && !normalized.endsWith(".txt")
                && !normalized.endsWith(".yaml")
                && !normalized.endsWith(".yml")) {
            throw new BusinessException(KnowledgeErrorCode.UNSUPPORTED_FILE_TYPE);
        }
    }

    private String safeFileName(String fileName) {
        String normalized = fileName.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        String safe = separator >= 0 ? normalized.substring(separator + 1) : normalized;
        return safe.replaceAll("[^\\p{L}\\p{N}._-]", "_");
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 Java 环境不支持 SHA-256", exception);
        }
    }

    private KnowledgeDocument requireDocument(Long projectId, Long documentId) {
        return repository.findDocument(projectId, documentId)
                .orElseThrow(() -> new BusinessException(KnowledgeErrorCode.DOCUMENT_NOT_FOUND));
    }

    private void requireProject(Long projectId) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));
    }

    private void markFailed(Long documentId, Exception exception) {
        LOGGER.error("文档索引异常，documentId={}", documentId, exception);
        repository.markFailed(
                documentId,
                limitMessage("内部处理失败：" + exception.getClass().getSimpleName())
        );
    }

    private String limitMessage(String value) {
        String message = value == null || value.isBlank() ? "未知错误" : value;
        return message.length() <= 2000 ? message : message.substring(0, 2000);
    }
}
