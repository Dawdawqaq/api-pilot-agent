package com.dochelper.retrieval.application;

import java.util.List;
import java.util.Map;

import com.dochelper.knowledge.config.KnowledgeProperties;
import com.dochelper.knowledge.domain.DocumentStatus;
import com.dochelper.knowledge.domain.KnowledgeChunk;
import com.dochelper.knowledge.domain.KnowledgeDocument;
import com.dochelper.knowledge.domain.repository.KnowledgeRepository;
import com.dochelper.project.domain.repository.ApiProjectRepository;
import com.dochelper.retrieval.domain.RetrievalResult;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStoreRetriever;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 RRF 融合排序及来源引用生成。
 */
class HybridRetrievalServiceTest {

    @Test
    void shouldPromoteCandidateRecalledByBothChannels() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        when(repository.findDocuments(1L)).thenReturn(List.of(document(101L, "login.md")));
        HybridRetrievalService service = new HybridRetrievalService(
                repository,
                mock(ApiProjectRepository.class),
                mock(VectorStoreRetriever.class),
                new KeywordTokenizer(),
                new KnowledgeProperties(10 * 1024 * 1024, 500_000, 800, 120, 20, 60)
        );
        KnowledgeChunk keywordOnly = chunk(1L, 101L, 0, "登录接口");
        KnowledgeChunk shared = chunk(2L, 101L, 1, "认证规则");
        Document vectorShared = vectorDocument(shared, "login.md");
        Document vectorOnly = Document.builder()
                .id("3")
                .text("令牌刷新")
                .metadata(Map.of(
                        "chunk_id", 3L,
                        "document_id", 101L,
                        "source_name", "login.md",
                        "section", "令牌刷新",
                        "chunk_index", 2
                ))
                .build();

        List<RetrievalResult> results = service.fuse(
                1L,
                List.of(keywordOnly, shared),
                List.of(vectorShared, vectorOnly),
                3
        );

        assertThat(results).extracting(RetrievalResult::chunkId)
                .containsExactly(2L, 1L, 3L);
        assertThat(results.get(0).keywordRank()).isEqualTo(2);
        assertThat(results.get(0).vectorRank()).isEqualTo(1);
        assertThat(results.get(0).citation())
                .isEqualTo("[login.md#认证规则/chunk-1]");
    }

    private KnowledgeDocument document(long id, String fileName) {
        return new KnowledgeDocument(
                id, 1L, fileName, "object-key", "text/markdown", 100,
                "hash", "登录指南", DocumentStatus.INDEXED, null, 2,
                null, null, null
        );
    }

    private KnowledgeChunk chunk(long id, long documentId, int index, String section) {
        return new KnowledgeChunk(
                id, 1L, documentId, index, section, section + "正文",
                10, "hash-" + id, String.valueOf(id)
        );
    }

    private Document vectorDocument(KnowledgeChunk chunk, String sourceName) {
        return Document.builder()
                .id(chunk.vectorId())
                .text(chunk.content())
                .metadata(Map.of(
                        "chunk_id", chunk.id(),
                        "document_id", chunk.documentId(),
                        "source_name", sourceName,
                        "section", chunk.sectionTitle(),
                        "chunk_index", chunk.chunkIndex()
                ))
                .build();
    }
}
