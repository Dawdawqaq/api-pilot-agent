package com.dochelper.model;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;

/**
 * DeepSeek 与通用 OpenAI 兼容协议模型配置。
 */
@Configuration(proxyBeanMethods = false)
@Profile("deepseek")
public class DeepSeekAiConfiguration {

    /**
     * 创建向量模型。当配置了有效的 embeddingModel 且不为 deterministic-local 时使用通用 OpenAI 兼容模型，否则优雅降级为本地确定性实现。
     *
     * @param properties 模型服务配置
     * @return 向量模型
     */
    @Bean
    EmbeddingModel deepSeekEmbeddingModel(ModelProviderProperties properties) {
        String embeddingModel = properties.embeddingModel();
        if (StringUtils.hasText(embeddingModel) && !"deterministic-local".equalsIgnoreCase(embeddingModel.trim())) {
            String effectiveBaseUrl = properties.effectiveEmbeddingBaseUrl();
            String effectiveApiKey = properties.effectiveEmbeddingApiKey();
            if (!StringUtils.hasText(effectiveApiKey)) {
                throw new IllegalStateException(
                        "启用真实 Embedding 模型时缺少有效 API Key，请配置 AI_EMBEDDING_API_KEY 或 AI_API_KEY"
                );
            }
            OpenAiApi api = OpenAiApi.builder()
                    .baseUrl(effectiveBaseUrl)
                    .apiKey(effectiveApiKey)
                    .build();
            OpenAiEmbeddingOptions.Builder optionsBuilder = OpenAiEmbeddingOptions.builder()
                    .model(embeddingModel.trim());
            if (properties.embeddingDimensions() != null && properties.embeddingDimensions() > 0) {
                optionsBuilder.dimensions(properties.embeddingDimensions());
            }
            return new OpenAiEmbeddingModel(
                    api,
                    org.springframework.ai.document.MetadataMode.EMBED,
                    optionsBuilder.build()
            );
        }
        return new DeterministicEmbeddingModel();
    }

}
