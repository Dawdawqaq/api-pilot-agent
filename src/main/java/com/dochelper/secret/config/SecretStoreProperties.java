package com.dochelper.secret.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 持久化 SecretStore 加密配置。
 */
@ConfigurationProperties(prefix = "dochelper.secret-store")
public record SecretStoreProperties(String masterKey) {
}
