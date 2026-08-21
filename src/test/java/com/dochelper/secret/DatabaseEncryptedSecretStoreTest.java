package com.dochelper.secret;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import com.dochelper.secret.config.SecretStoreProperties;
import com.dochelper.secret.infrastructure.DatabaseEncryptedSecretStore;
import com.dochelper.secret.infrastructure.persistence.entity.RuntimeSecretEntity;
import com.dochelper.secret.infrastructure.persistence.mapper.RuntimeSecretMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证运行时秘密以 AES-GCM 密文保存，并能通过不透明引用恢复。
 */
class DatabaseEncryptedSecretStoreTest {

    @Test
    void shouldPersistCiphertextAndRestoreByOpaqueReference() {
        RuntimeSecretMapper mapper = mock(RuntimeSecretMapper.class);
        AtomicReference<RuntimeSecretEntity> stored = new AtomicReference<>();
        doAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return 1;
        }).when(mapper).insert(any(RuntimeSecretEntity.class));
        when(mapper.selectById(any())).thenAnswer(invocation -> {
            RuntimeSecretEntity entity = stored.get();
            return entity != null && entity.getSecretRef().equals(invocation.getArgument(0))
                    ? entity
                    : null;
        });
        DatabaseEncryptedSecretStore store = new DatabaseEncryptedSecretStore(
                mapper,
                new SecretStoreProperties("stable-test-master-key")
        );

        String reference = store.put("task:1", "Bearer raw-secret-token", Duration.ofMinutes(5));

        assertThat(reference).startsWith("db:");
        assertThat(new String(stored.get().getEncryptedValue(), StandardCharsets.UTF_8))
                .doesNotContain("raw-secret-token");
        assertThat(stored.get().getScopeHash()).hasSize(64).doesNotContain("task:1");
        assertThat(store.get(reference)).contains("Bearer raw-secret-token");
    }
}
