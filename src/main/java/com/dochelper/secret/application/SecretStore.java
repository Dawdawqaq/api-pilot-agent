package com.dochelper.secret.application;

import java.time.Duration;
import java.util.Optional;

/**
 * 运行时敏感值存储，只向业务表暴露不可逆引用。
 */
public interface SecretStore {

    String put(String scope, String value, Duration ttl);

    Optional<String> get(String reference);

    void delete(String reference);
}
