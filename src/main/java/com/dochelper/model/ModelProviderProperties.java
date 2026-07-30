package com.dochelper.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenAI 兼容模型服务配置。
 *
 * @param baseUrl 模型服务基础地址
 * @param apiKey 模型服务密钥
 * @param chatModel 对话模型名称
 * @param embeddingModel 向量模型名称
 */
@ConfigurationProperties(prefix = "dochelper.ai")
public record ModelProviderProperties(
        String baseUrl,
        String apiKey,
        String chatModel,
        String embeddingModel
) {
}
