package com.dochelper.retrieval.application;

import java.util.Optional;

import com.dochelper.common.exception.BusinessException;
import com.dochelper.knowledge.application.KnowledgeDocumentService;
import com.dochelper.knowledge.application.SectionAwareChunker;
import com.dochelper.knowledge.application.TikaDocumentExtractor;
import com.dochelper.knowledge.config.KnowledgeProperties;
import com.dochelper.knowledge.domain.repository.KnowledgeRepository;
import com.dochelper.knowledge.exception.KnowledgeErrorCode;
import com.dochelper.project.domain.ApiProject;
import com.dochelper.project.domain.repository.ApiProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 知识库禁用后不得读取旧证据或因缺失外部组件而产生空指针异常。
 */
class OptionalKnowledgeTest {
    private final KnowledgeProperties properties = new KnowledgeProperties(1024, 10000, 800, 120, 20, 60);

    @Test
    void shouldSkipKnowledgeRepositoriesInCoreMode() {
        var repository = mock(KnowledgeRepository.class);
        var projects = mock(ApiProjectRepository.class);
        when(projects.findById(1L)).thenReturn(Optional.of(mock(ApiProject.class)));
        var service = new HybridRetrievalService(repository, projects, null, new KeywordTokenizer(), properties);
        ReflectionTestUtils.setField(service, "knowledgeEnabled", false);
        assertThat(service.search(1L, "登录并查询", 5)).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void shouldReportDisabledInsteadOfMutatingDocumentsWithoutStorage() {
        var repository = mock(KnowledgeRepository.class);
        var service = new KnowledgeDocumentService(repository, mock(ApiProjectRepository.class), null,
                mock(TikaDocumentExtractor.class), mock(SectionAwareChunker.class), null, properties);
        assertThatThrownBy(() -> service.upload(1L, "rules.txt", "text/plain", new byte[]{1}))
                .isInstanceOf(BusinessException.class).extracting("errorCode")
                .isEqualTo(KnowledgeErrorCode.FEATURE_DISABLED);
        assertThatThrownBy(() -> service.delete(1L, 2L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void shouldNotSilentlyDegradeMisconfiguredKnowledgeMode() {
        var projects = mock(ApiProjectRepository.class);
        when(projects.findById(1L)).thenReturn(Optional.of(mock(ApiProject.class)));
        var service = new HybridRetrievalService(mock(KnowledgeRepository.class), projects, null,
                new KeywordTokenizer(), properties);
        assertThatThrownBy(() -> service.search(1L, "登录", 5)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(KnowledgeErrorCode.FEATURE_DISABLED);
    }
}
