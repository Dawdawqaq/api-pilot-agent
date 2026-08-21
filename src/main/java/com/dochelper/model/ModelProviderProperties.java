package com.dochelper.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenAI 兼容模型服务配置。
 *
 * @param baseUrl 模型服务基础地址
 * @param apiKey 模型服务密钥
 * @param chatModel 对话模型名称
 * @param embeddingBaseUrl 独立向量模型服务基础地址（可选，未提供时默认使用 baseUrl）
 * @param embeddingApiKey 独立向量模型服务密钥（可选，未提供时默认使用 apiKey）
 * @param embeddingModel 向量模型名称（如 text-embedding-3-small、text-embedding-v4、BAAI/bge-m3 等，未配置或为 deterministic-local 则使用本地确定性回退）
 * @param embeddingDimensions 向量输出维度（可选）
 */
@ConfigurationProperties(prefix = "dochelper.ai")
public record ModelProviderProperties(
        String baseUrl,
        String apiKey,
        String chatModel,
        String embeddingBaseUrl,
        String embeddingApiKey,
        String embeddingModel,
        Integer embeddingDimensions
) {

    /**
     * 获取有效的向量服务基础地址。
     *
     * @return 若显式配置了 embeddingBaseUrl 则返回它，否则回退到 baseUrl
     */
    public String effectiveEmbeddingBaseUrl() {
        return embeddingBaseUrl != null && !embeddingBaseUrl.isBlank() ? embeddingBaseUrl : baseUrl;
    }

    /**
     * 获取有效的向量服务密钥。
     *
     * @return 若显式配置了 embeddingApiKey 则返回它，否则回退到 apiKey
     */
    public String effectiveEmbeddingApiKey() {
        return embeddingApiKey != null && !embeddingApiKey.isBlank() ? embeddingApiKey : apiKey;
    }
}
