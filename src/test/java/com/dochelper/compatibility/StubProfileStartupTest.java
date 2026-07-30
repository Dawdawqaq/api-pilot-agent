package com.dochelper.compatibility;

import com.dochelper.compatibility.stub.StubAiConfiguration;
import com.dochelper.model.ModelProviderProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证无真实 API Key 时的 Stub Profile 可以完整启动。
 */
class StubProfileStartupTest {

    /**
     * 验证 Stub 模型和模型配置均已装配。
     */
    @Test
    void shouldStartWithoutRealApiKey() {
        new ApplicationContextRunner()
                .withUserConfiguration(StubTestConfiguration.class)
                .withPropertyValues(
                        "spring.profiles.active=stub",
                        "dochelper.ai.base-url=https://example.invalid",
                        "dochelper.ai.api-key=",
                        "dochelper.ai.chat-model=qwen-plus",
                        "dochelper.ai.embedding-model=text-embedding-v4"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(ChatModel.class);
                    assertThat(context).hasSingleBean(EmbeddingModel.class);

                    EmbeddingModel embeddingModel = context.getBean(EmbeddingModel.class);
                    ModelProviderProperties properties = context.getBean(ModelProviderProperties.class);
                    assertThat(embeddingModel.dimensions()).isEqualTo(4);
                    assertThat(properties.apiKey()).isEmpty();
                    assertThat(properties.chatModel()).isEqualTo("qwen-plus");
                });
    }

    /**
     * 仅装配 Stub 模型及其配置属性，避免兼容性测试加载业务基础设施。
     */
    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ModelProviderProperties.class)
    @Import(StubAiConfiguration.class)
    static class StubTestConfiguration {
    }
}
