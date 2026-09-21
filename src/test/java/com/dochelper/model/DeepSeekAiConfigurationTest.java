package com.dochelper.model;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 DeepSeek 与通用 OpenAI 兼容协议模型配置。
 */
class DeepSeekAiConfigurationTest {

    private final DeepSeekAiConfiguration configuration = new DeepSeekAiConfiguration();

    @Test
    void shouldFallbackToDeterministicWhenModelIsLocalOrNull() {
        ModelProviderProperties defaultProps = new ModelProviderProperties(
                "https://api.deepseek.com", "test-key", "deepseek-flash",
                null, null, "deterministic-local", null
        );
        EmbeddingModel model = configuration.deepSeekEmbeddingModel(defaultProps);
        assertThat(model).isInstanceOf(DeterministicEmbeddingModel.class);

        ModelProviderProperties nullProps = new ModelProviderProperties(
                "https://api.deepseek.com", "test-key", "deepseek-flash",
                null, null, null, null
        );
        EmbeddingModel nullModel = configuration.deepSeekEmbeddingModel(nullProps);
        assertThat(nullModel).isInstanceOf(DeterministicEmbeddingModel.class);
    }

    @Test
    void shouldCreateOpenAiEmbeddingModelWhenExternalModelConfigured() {
        ModelProviderProperties props = new ModelProviderProperties(
                "https://api.deepseek.com", "test-key", "deepseek-flash",
                "https://api.openai.com", "openai-key", "text-embedding-3-small", 1536
        );
        EmbeddingModel model = configuration.deepSeekEmbeddingModel(props);
        assertThat(model).isInstanceOf(OpenAiEmbeddingModel.class);
    }

    @Test
    void shouldInheritChatApiKeyWhenEmbeddingApiKeyNotSpecified() {
        ModelProviderProperties props = new ModelProviderProperties(
                "https://api.openai.com/v1", "shared-key", "gpt-4o-mini",
                null, null, "text-embedding-3-small", null
        );
        EmbeddingModel model = configuration.deepSeekEmbeddingModel(props);
        assertThat(model).isInstanceOf(OpenAiEmbeddingModel.class);
    }

    @Test
    void shouldThrowExceptionWhenEmbeddingModelConfiguredWithoutAnyApiKey() {
        ModelProviderProperties props = new ModelProviderProperties(
                "https://api.openai.com/v1", null, "gpt-4o-mini",
                null, null, "text-embedding-3-small", null
        );
        assertThatThrownBy(() -> configuration.deepSeekEmbeddingModel(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("缺少有效 API Key");
    }
}
