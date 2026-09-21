package com.dochelper.retrieval.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.dochelper.common.exception.BusinessException;
import com.dochelper.knowledge.config.KnowledgeProperties;
import com.dochelper.knowledge.domain.KnowledgeChunk;
import com.dochelper.knowledge.domain.KnowledgeDocument;
import com.dochelper.knowledge.domain.repository.KnowledgeRepository;
import com.dochelper.knowledge.exception.KnowledgeErrorCode;
import com.dochelper.project.domain.repository.ApiProjectRepository;
import com.dochelper.project.exception.ProjectErrorCode;
import com.dochelper.retrieval.domain.RetrievalResult;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStoreRetriever;
import org.springframework.stereotype.Service;

/**
 * 融合 MySQL 关键词召回与 Qdrant 向量召回的检索服务。
 */
@Service
public class HybridRetrievalService {

    private final KnowledgeRepository repository;
    private final ApiProjectRepository projectRepository;
    private final VectorStoreRetriever vectorRetriever;
    private final KeywordTokenizer tokenizer;
    private final KnowledgeProperties properties;
    @org.springframework.beans.factory.annotation.Value("${dochelper.knowledge.enabled:true}")
    private boolean knowledgeEnabled = true;

    public HybridRetrievalService(
            KnowledgeRepository repository,
            ApiProjectRepository projectRepository,
            @org.springframework.lang.Nullable VectorStoreRetriever vectorRetriever,
            KeywordTokenizer tokenizer,
            KnowledgeProperties properties
    ) {
        this.repository = repository;
        this.projectRepository = projectRepository;
        this.vectorRetriever = vectorRetriever;
        this.tokenizer = tokenizer;
        this.properties = properties;
    }

    public List<RetrievalResult> search(Long projectId, String query, int topK) {
        requireProject(projectId);
        if (query == null || query.isBlank()) {
            throw new BusinessException(KnowledgeErrorCode.EMPTY_QUERY);
        }
        // 核心模式明确不使用知识库证据，不能用伪造向量结果冒充检索。
        if (!knowledgeEnabled) {
            return List.of();
        }
        if (vectorRetriever == null) {
            throw new BusinessException(KnowledgeErrorCode.FEATURE_DISABLED);
        }
        int safeTopK = Math.max(1, Math.min(topK, 20));
        int candidateSize = Math.max(safeTopK, properties.retrievalCandidateSize());
        List<KnowledgeChunk> keywordResults = keywordSearch(projectId, query.trim(), candidateSize);
        List<Document> vectorResults = vectorRetriever.similaritySearch(SearchRequest.builder()
                .query(query.trim())
                .topK(candidateSize)
                .similarityThreshold(0.0)
                .filterExpression("project_id == '" + projectId + "'")
                .build());
        return fuse(projectId, keywordResults, vectorResults, safeTopK);
    }

    /**
     * 判断当前运行模式是否启用了业务知识检索。
     *
     * @return 是否启用业务知识检索
     */
    public boolean isKnowledgeEnabled() {
        return knowledgeEnabled;
    }

    List<RetrievalResult> fuse(
            Long projectId,
            List<KnowledgeChunk> keywordResults,
            List<Document> vectorResults,
            int topK
    ) {
        Map<Long, Candidate> candidates = new LinkedHashMap<>();
        Map<Long, String> sourceNames = new HashMap<>();
        repository.findDocuments(projectId).forEach(document ->
                sourceNames.put(document.id(), document.fileName())
        );

        for (int index = 0; index < keywordResults.size(); index++) {
            KnowledgeChunk chunk = keywordResults.get(index);
            Candidate candidate = candidates.computeIfAbsent(chunk.id(), ignored ->
                    Candidate.fromChunk(chunk, sourceNames.getOrDefault(chunk.documentId(), "unknown"))
            );
            candidate.keywordRank = index + 1;
            candidate.score += reciprocalRank(index + 1);
        }
        for (int index = 0; index < vectorResults.size(); index++) {
            Document document = vectorResults.get(index);
            Long chunkId = metadataLong(document, "chunk_id");
            if (chunkId == null) {
                continue;
            }
            Candidate candidate = candidates.computeIfAbsent(chunkId, ignored ->
                    Candidate.fromVector(document)
            );
            candidate.vectorRank = index + 1;
            candidate.score += reciprocalRank(index + 1);
        }

        return candidates.values().stream()
                .sorted(Comparator.comparingDouble(Candidate::score).reversed()
                        .thenComparingLong(Candidate::chunkId))
                .limit(topK)
                .map(Candidate::toResult)
                .toList();
    }

    private List<KnowledgeChunk> keywordSearch(Long projectId, String query, int limit) {
        Map<Long, KeywordCandidate> candidates = new HashMap<>();
        List<String> terms = tokenizer.tokenize(query);
        for (String term : terms) {
            List<KnowledgeChunk> termResults = repository.keywordSearch(projectId, term, limit);
            for (int rank = 0; rank < termResults.size(); rank++) {
                KnowledgeChunk chunk = termResults.get(rank);
                KeywordCandidate candidate = candidates.computeIfAbsent(
                        chunk.id(),
                        ignored -> new KeywordCandidate(chunk)
                );
                candidate.score += 1.0 / (rank + 1);
            }
        }
        return candidates.values().stream()
                .sorted(Comparator.comparingDouble(KeywordCandidate::score).reversed()
                        .thenComparing(candidate -> candidate.chunk.id()))
                .limit(limit)
                .map(candidate -> candidate.chunk)
                .toList();
    }

    private double reciprocalRank(int rank) {
        return 1.0 / (properties.rrfRankConstant() + rank);
    }

    private Long metadataLong(Document document, String key) {
        Object value = document.getMetadata().get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void requireProject(Long projectId) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));
    }

    private static final class KeywordCandidate {
        private final KnowledgeChunk chunk;
        private double score;

        private KeywordCandidate(KnowledgeChunk chunk) {
            this.chunk = chunk;
        }

        private double score() {
            return score;
        }
    }

    private static final class Candidate {
        private final long chunkId;
        private final long documentId;
        private final String sourceName;
        private final String section;
        private final int chunkIndex;
        private final String content;
        private double score;
        private Integer keywordRank;
        private Integer vectorRank;

        private Candidate(
                long chunkId,
                long documentId,
                String sourceName,
                String section,
                int chunkIndex,
                String content
        ) {
            this.chunkId = chunkId;
            this.documentId = documentId;
            this.sourceName = sourceName;
            this.section = section;
            this.chunkIndex = chunkIndex;
            this.content = content;
        }

        private static Candidate fromChunk(KnowledgeChunk chunk, String sourceName) {
            return new Candidate(
                    chunk.id(),
                    chunk.documentId(),
                    sourceName,
                    chunk.sectionTitle(),
                    chunk.chunkIndex(),
                    chunk.content()
            );
        }

        private static Candidate fromVector(Document document) {
            Map<String, Object> metadata = document.getMetadata();
            return new Candidate(
                    longValue(metadata.get("chunk_id")),
                    longValue(metadata.get("document_id")),
                    stringValue(metadata.get("source_name"), "unknown"),
                    stringValue(metadata.get("section"), null),
                    intValue(metadata.get("chunk_index")),
                    document.getText()
            );
        }

        private static long longValue(Object value) {
            return value instanceof Number number
                    ? number.longValue()
                    : Long.parseLong(value.toString());
        }

        private static int intValue(Object value) {
            return value instanceof Number number
                    ? number.intValue()
                    : Integer.parseInt(value.toString());
        }

        private static String stringValue(Object value, String fallback) {
            return value == null ? fallback : value.toString();
        }

        private long chunkId() { return chunkId; }
        private double score() { return score; }

        private RetrievalResult toResult() {
            String safeSection = section == null || section.isBlank() ? "正文" : section;
            return new RetrievalResult(
                    chunkId,
                    documentId,
                    sourceName,
                    safeSection,
                    chunkIndex,
                    content,
                    score,
                    keywordRank,
                    vectorRank,
                    "[" + sourceName + "#" + safeSection + "/chunk-" + chunkIndex + "]"
            );
        }
    }
}
