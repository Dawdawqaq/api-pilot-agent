package com.dochelper.model.domain;

/** 应用级对话模型配置，数据库仅保存密钥引用。 */
public record LlmConfiguration(String mode, String provider, String baseUrl, String model, String secretReference) {
}
