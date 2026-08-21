package com.dochelper.secret.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 测试环境使用的进程内 SecretStore，生产实现由阶段 13 提供。
 */
@Component
@Profile("test")
public class InMemorySecretStore implements SecretStore {

    private final ConcurrentHashMap<String, StoredSecret> secrets = new ConcurrentHashMap<>();

    @Override
    public String put(String scope, String value, Duration ttl) {
        String reference = "mem:" + UUID.randomUUID();
        secrets.put(reference, new StoredSecret(value, Instant.now().plus(ttl)));
        return reference;
    }

    @Override
    public Optional<String> get(String reference) {
        StoredSecret secret = secrets.get(reference);
        if (secret == null || Instant.now().isAfter(secret.expiresAt())) {
            secrets.remove(reference);
            return Optional.empty();
        }
        return Optional.of(secret.value());
    }

    @Override
    public void delete(String reference) {
        secrets.remove(reference);
    }

    private record StoredSecret(String value, Instant expiresAt) {
    }
}
