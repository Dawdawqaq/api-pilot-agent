package com.dochelper.model;

import java.util.Map;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

/**
 * DeepSeek OpenAI 兼容协议模型配置。
 */
@Configuration(proxyBeanMethods = false)
@Profile("deepseek")
public class DeepSeekAiConfiguration {

    /**
     * 创建用于真实规划的 DeepSeek 对话模型。
     *
     * @param properties 模型服务配置
     * @return 对话模型
     */
    @Bean
    ChatModel deepSeekChatModel(ModelProviderProperties properties) {
        validate(properties);
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(properties.baseUrl())
                .apiKey(properties.apiKey())
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(properties.chatModel())
                .temperature(0.1)
                .maxTokens(4096)
                .parallelToolCalls(false)
                .extraBody(Map.of(
                        "thinking",
                        Map.of("type", "disabled")
                ))
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }

    /**
     * DeepSeek 尚未提供 Embedding API，阶段 7 明确使用本地确定性降级实现。
     *
     * @return 本地确定性向量模型
     */
    @Bean
    EmbeddingModel deepSeekFallbackEmbeddingModel() {
        return new DeterministicEmbeddingModel();
    }

    private void validate(ModelProviderProperties properties) {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new IllegalStateException(
                    "deepseek Profile 缺少 AI_API_KEY，真实密钥只能通过环境变量注入"
            );
        }
        if (!StringUtils.hasText(properties.baseUrl())
                || !StringUtils.hasText(properties.chatModel())) {
            throw new IllegalStateException(
                    "deepseek Profile 缺少 AI_BASE_URL 或 AI_CHAT_MODEL"
            );
        }
    }
}
