package com.dochelper.model.api.vo;

/** 脱敏配置响应，任何情况下均不返回密钥或密钥引用。 */
public record LlmConfigurationResponse(String mode, String provider, String baseUrl, String model,
        String activeModel, boolean apiKeyConfigured, String source, boolean persistentStorageReady) {
}
