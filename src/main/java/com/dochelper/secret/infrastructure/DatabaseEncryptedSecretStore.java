package com.dochelper.secret.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.dochelper.secret.application.SecretStore;
import com.dochelper.secret.config.SecretStoreProperties;
import com.dochelper.secret.infrastructure.persistence.entity.RuntimeSecretEntity;
import com.dochelper.secret.infrastructure.persistence.mapper.RuntimeSecretMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 使用外部主密钥和 AES-GCM 加密落库的 SecretStore。
 */
@Component
@Profile("!test")
public class DatabaseEncryptedSecretStore implements SecretStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseEncryptedSecretStore.class);
    private static final int GCM_TAG_BITS = 128;

    private final RuntimeSecretMapper mapper;
    private final SecretKey key;
    private final SecureRandom secureRandom = new SecureRandom();

    public DatabaseEncryptedSecretStore(
            RuntimeSecretMapper mapper,
            SecretStoreProperties properties
    ) {
        this.mapper = mapper;
        String configured = properties.masterKey();
        if (configured == null || configured.isBlank()) {
            byte[] temporary = new byte[32];
            secureRandom.nextBytes(temporary);
            this.key = new SecretKeySpec(temporary, "AES");
            LOGGER.warn("SECRET_STORE_MASTER_KEY 未配置，运行时秘密在应用重启后不可恢复");
        } else {
            this.key = new SecretKeySpec(sha256Bytes(configured), "AES");
        }
    }

    @Override
    public String put(String scope, String value, Duration ttl) {
        try {
            String reference = "db:" + UUID.randomUUID();
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(reference.getBytes(StandardCharsets.UTF_8));
            RuntimeSecretEntity entity = new RuntimeSecretEntity();
            entity.setSecretRef(reference);
            entity.setScopeHash(HexFormat.of().formatHex(sha256Bytes(scope)));
            entity.setEncryptedValue(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
            entity.setInitializationVector(iv);
            entity.setExpiresAt(LocalDateTime.now().plus(ttl));
            entity.setCreatedAt(LocalDateTime.now());
            mapper.insert(entity);
            return reference;
        } catch (Exception exception) {
            throw new IllegalStateException("运行时敏感值加密失败", exception);
        }
    }

    @Override
    public Optional<String> get(String reference) {
        RuntimeSecretEntity entity = mapper.selectById(reference);
        if (entity == null || LocalDateTime.now().isAfter(entity.getExpiresAt())) {
            if (entity != null) {
                mapper.deleteById(reference);
            }
            return Optional.empty();
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    new GCMParameterSpec(GCM_TAG_BITS, entity.getInitializationVector())
            );
            cipher.updateAAD(reference.getBytes(StandardCharsets.UTF_8));
            return Optional.of(new String(
                    cipher.doFinal(entity.getEncryptedValue()), StandardCharsets.UTF_8
            ));
        } catch (Exception exception) {
            throw new IllegalStateException("运行时敏感值解密失败，请检查主密钥", exception);
        }
    }

    @Override
    public void delete(String reference) {
        mapper.deleteById(reference);
    }

    private byte[] sha256Bytes(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256 实现", exception);
        }
    }
}
