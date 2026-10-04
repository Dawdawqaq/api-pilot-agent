package com.dochelper.model;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.dochelper.common.exception.BusinessException;
import com.dochelper.model.api.dto.UpdateLlmConfigurationRequest;
import com.dochelper.model.application.LlmConfigurationService;
import com.dochelper.secret.application.InMemorySecretStore;
import com.dochelper.secret.config.SecretStoreProperties;
import com.dochelper.system.domain.model.SystemSetting;
import com.dochelper.system.domain.repository.SystemSettingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证持久配置、密钥脱敏、保留与替换，以及错误配置的边界。 */
class LlmConfigurationServiceTest {
    private final AtomicReference<String> persisted = new AtomicReference<>();
    private final InMemorySecretStore secrets = new InMemorySecretStore();
    private SystemSettingRepository repository;
    private TransactionTemplate transactions;
    private LlmConfigurationService service;

    @BeforeEach void setUp() {
        repository = mock(SystemSettingRepository.class);
        when(repository.findByKey(anyString())).thenAnswer(invocation -> Optional.ofNullable(persisted.get())
                .map(json -> new SystemSetting(1L, invocation.getArgument(0), json, "模型配置", LocalDateTime.now())));
        doAnswer(invocation -> { persisted.set(invocation.getArgument(1)); return null; })
                .when(repository).save(anyString(), anyString(), anyString());
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        transactions = new TransactionTemplate(manager);
        service = createService("stable-master-key");
    }

    private LlmConfigurationService createService(String masterKey) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("stub");
        return new LlmConfigurationService(repository, secrets, new ModelProviderProperties("https://api.deepseek.com",
                null, "local-stub", null, null, "deterministic-local", null), new SecretStoreProperties(masterKey),
                new ObjectMapper(), transactions, environment);
    }

    private UpdateLlmConfigurationRequest api(String model, String key) {
        return new UpdateLlmConfigurationRequest("API", "DEEPSEEK", "https://api.deepseek.com", model, key);
    }

    @Test void shouldSaveOpaqueReferenceAndRestoreAfterServiceRestart() throws Exception {
        var response = service.save(api("model-a", "secret-test-key"));
        assertThat(response.activeModel()).isEqualTo("model-a");
        assertThat(response.apiKeyConfigured()).isTrue();
        assertThat(persisted.get()).doesNotContain("secret-test-key").contains("mem:");
        assertThat(new ObjectMapper().writeValueAsString(response)).doesNotContain("secret-test-key", "secretReference", "apiKey\"");
        assertThat(createService("stable-master-key").resolveActive().apiKey()).isEqualTo("secret-test-key");
        assertThat(api("model-a", "secret-test-key").toString()).doesNotContain("secret-test-key");
    }

    @Test void shouldKeepKeyWhenBlankAndReplaceOldKeyWhenProvided() throws Exception {
        service.save(api("model-a", "old-key"));
        String oldReference = new ObjectMapper().readTree(persisted.get()).get("secretReference").asText();
        service.save(api("model-b", ""));
        assertThat(service.resolveActive().apiKey()).isEqualTo("old-key");
        service.save(api("model-b", "new-key"));
        assertThat(service.resolveActive().apiKey()).isEqualTo("new-key");
        assertThat(secrets.get(oldReference)).isEmpty();
    }

    @Test void shouldRejectMissingKeyAndUnsafeUrlWithoutChangingSavedConfiguration() {
        assertThatThrownBy(() -> service.save(api("model-a", ""))).isInstanceOf(BusinessException.class).hasMessageContaining("API Key");
        for (String url : java.util.List.of("file:///etc/passwd", "https://user:password@example.com", "https://example.com?api_key=x", "https://example.com/chat/completions")) {
            assertThatThrownBy(() -> service.save(new UpdateLlmConfigurationRequest("API", "DEEPSEEK", url, "a", "key")))
                    .isInstanceOf(BusinessException.class);
        }
        assertThat(persisted.get()).isNull();
    }

    @Test void shouldRequireNewKeyWhenChangingProviderOrAddress() {
        service.save(api("model-a", "old-key"));
        assertThatThrownBy(() -> service.save(new UpdateLlmConfigurationRequest("API", "OPENAI_COMPATIBLE",
                "https://example.com/v1", "model-b", ""))).isInstanceOf(BusinessException.class).hasMessageContaining("重新填写");
        assertThat(service.getConfiguration().model()).isEqualTo("model-a");
    }

    @Test void shouldTestDraftWithoutSavingAndAllowOfflineWithoutKey() {
        assertThat(service.resolveDraft(api("model-a", "draft-key")).apiKey()).isEqualTo("draft-key");
        assertThat(persisted.get()).isNull();
        service.save(new UpdateLlmConfigurationRequest("OFFLINE", "DEEPSEEK", "https://api.deepseek.com", "", ""));
        assertThat(service.resolveActive().apiKey()).isNull();
        assertThat(service.getConfiguration().activeModel()).isEqualTo("local-stub");
    }

    @Test void shouldRejectPersistentKeyWithoutStableMasterKey() {
        assertThatThrownBy(() -> createService("").save(api("model-a", "secret-key")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("SECRET_STORE_MASTER_KEY");
        assertThat(persisted.get()).isNull();
    }
}
