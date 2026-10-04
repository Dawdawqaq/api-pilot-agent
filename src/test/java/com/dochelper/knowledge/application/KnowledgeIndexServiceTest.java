package com.dochelper.knowledge.application;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.dochelper.infrastructure.config.InfrastructureEndpointProperties;
import com.dochelper.knowledge.api.dto.EmbeddingConfigurationRequest;
import com.dochelper.knowledge.domain.*;
import com.dochelper.knowledge.domain.repository.KnowledgeRepository;
import com.dochelper.model.DeterministicEmbeddingModel;
import com.dochelper.model.ModelProviderProperties;
import com.dochelper.secret.application.InMemorySecretStore;
import com.dochelper.secret.config.SecretStoreProperties;
import com.dochelper.system.domain.model.SystemSetting;
import com.dochelper.system.domain.repository.SystemSettingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 验证配置切换只发生在完整索引成功后，独立密钥不污染对话配置。 */
class KnowledgeIndexServiceTest {
    private final Map<String, String> persisted = new HashMap<>();
    private final SystemSettingRepository settings = mock(SystemSettingRepository.class);
    private final KnowledgeRepository knowledge = mock(KnowledgeRepository.class);
    private final EmbeddingModelFactory factory = mock(EmbeddingModelFactory.class);
    private final VectorStore original = mock(VectorStore.class), next = mock(VectorStore.class);
    private final EmbeddingModel remote = mock(EmbeddingModel.class);
    private final InMemorySecretStore secrets = new InMemorySecretStore();
    private TransactionTemplate transactions;
    private KnowledgeIndexService service;

    @BeforeEach void prepare() {
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        transactions = new TransactionTemplate(manager);
        when(settings.findByKey(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return Optional.ofNullable(persisted.get(key)).map(value -> new SystemSetting(1L,key,value,"",LocalDateTime.now()));
        });
        doAnswer(invocation -> { persisted.put(invocation.getArgument(0),invocation.getArgument(1)); return null; })
                .when(settings).save(anyString(),anyString(),anyString());
        when(knowledge.findIndexableDocuments()).thenReturn(List.of(new KnowledgeDocument(2L,1L,"rules.md","key","text/plain",
                1,"hash","规则",DocumentStatus.INDEXED,null,1,null,null,null)));
        when(knowledge.findChunks(2L)).thenReturn(List.of(new KnowledgeChunk(3L,1L,2L,0,"规则","规则正文",4,"hash",java.util.UUID.randomUUID().toString())));
        when(factory.create(eq("API"),anyString(),anyString(),nullable(Integer.class),anyString())).thenReturn(remote);
        when(remote.embed(anyString())).thenReturn(new float[]{1,0});
        when(factory.create(eq("DEVELOPMENT"),anyString(),anyString(),isNull(),isNull())).thenReturn(new DeterministicEmbeddingModel());
        service = create();
    }

    @SuppressWarnings("unchecked") private KnowledgeIndexService create() {
        ObjectProvider<VectorStore> stores = mock(ObjectProvider.class);
        when(stores.getIfAvailable()).thenReturn(original); when(stores.getObject()).thenReturn(original);
        ObjectProvider<EmbeddingModel> models = mock(ObjectProvider.class);
        when(models.getIfAvailable()).thenReturn(new DeterministicEmbeddingModel());
        var result = spy(new KnowledgeIndexService(stores,models,mock(ObjectProvider.class),settings,secrets,knowledge,
                factory,new ModelProviderProperties("https://chat.invalid","CHAT_ONLY","chat-model","","","deterministic-local",null),
                new SecretStoreProperties("stable-key"),new ObjectMapper(),transactions,
                new InfrastructureEndpointProperties(true,"http://localhost:6333","dochelper_knowledge"),WebClient.builder()));
        ReflectionTestUtils.setField(result,"enabled",true);
        doReturn(next).when(result).createStore(anyString(),any(),anyBoolean());
        doNothing().when(result).removeGeneration(anyString());
        return result;
    }

    private EmbeddingConfigurationRequest request(boolean transfer) {
        return new EmbeddingConfigurationRequest("API","https://embedding.invalid/v1","embed-model",null,"EMBED_KEY",transfer);
    }

    @Test void shouldRequireIndependentKeyAndExplicitDocumentTransfer() {
        assertThatThrownBy(() -> service.test(new EmbeddingConfigurationRequest("API","https://embedding.invalid/v1",
                "embed-model",null,"",false))).hasMessageContaining("独立");
        assertThatThrownBy(() -> service.rebuild(request(false))).hasMessageContaining("明确允许");
        verify(remote,never()).embed(anyString()); verify(next,never()).add(anyList());
        assertThat(persisted).isEmpty();
    }

    @Test void shouldKeepOriginalIndexAfterFailedBuild() {
        doThrow(new IllegalStateException("供应商失败")).when(next).add(anyList());
        assertThatThrownBy(() -> service.rebuild(request(true))).hasMessageContaining("原配置和索引继续生效");
        assertThat(persisted).doesNotContainKey("llm.embedding.configuration");
        assertThat(service.configuration().mode()).isEqualTo("DEVELOPMENT");
        assertThat(service.configuration().rebuilding()).isFalse();
        verify(service).removeGeneration(startsWith("dochelper_knowledge_g_"));
    }

    @Test void shouldActivateOnlyAfterBuildAndRestoreWithoutForcingDimensionsParameter() throws Exception {
        var response = service.rebuild(request(true));
        assertThat(response.mode()).isEqualTo("API"); assertThat(response.dimensions()).isEqualTo(2);
        assertThat(response.requestedDimensions()).isNull();
        assertThat(response.rebuilding()).isFalse(); assertThat(response.apiKeyConfigured()).isTrue();
        assertThat(response.collection()).startsWith("dochelper_knowledge_g_");
        assertThat(persisted.get("llm.embedding.configuration")).doesNotContain("EMBED_KEY","CHAT_ONLY");
        assertThat(new ObjectMapper().writeValueAsString(response)).doesNotContain("EMBED_KEY","secretReference");
        verify(next).add(argThat(documents -> documents.size() == 1 && "1".equals(documents.getFirst().getMetadata().get("project_id"))));
        KnowledgeIndexService restarted = create();
        restarted.add(List.of());
        verify(factory,times(2)).create("API","https://embedding.invalid/v1","embed-model",null,"EMBED_KEY");
    }

    @Test void shouldReportDimensionMismatchWithoutSavingDraft() {
        var result = service.test(new EmbeddingConfigurationRequest("API","https://embedding.invalid/v1","embed-model",3,"EMBED_KEY",false));
        assertThat(result.get("success")).isEqualTo(false); assertThat(persisted).isEmpty();
    }

    @Test void shouldBlockOldVectorsAfterBackupRestorationUntilSuccessfulRebuild() {
        persisted.put("knowledge.index.restore.required","true");
        assertThat(service.configuration().rebuildRequired()).isTrue();
        assertThatThrownBy(() -> service.add(List.of())).hasMessageContaining("重建索引");
        service.rebuild(new EmbeddingConfigurationRequest("DEVELOPMENT","","",null,"",false));
        assertThat(persisted.get("knowledge.index.restore.required")).isEqualTo("false");
        assertThat(service.configuration().dimensions()).isEqualTo(4);
    }
}
