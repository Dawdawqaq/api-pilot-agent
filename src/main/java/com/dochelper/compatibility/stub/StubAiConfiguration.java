package com.dochelper.compatibility.stub;

import com.dochelper.model.DeterministicEmbeddingModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 无真实 API Key 时使用的阶段 0 模型配置。
 */
@Configuration(proxyBeanMethods = false)
@Profile("stub")
public class StubAiConfiguration {

    /**
     * 创建固定响应的对话模型。
     *
     * @return Stub 对话模型
     */
    @Bean
    ChatModel stubChatModel() {
        return new StubChatModel("阶段 0 Stub 响应");
    }

    /**
     * 创建确定性向量模型。
     *
     * @return Stub 向量模型
     */
    @Bean
    EmbeddingModel deterministicEmbeddingModel() {
        return new DeterministicEmbeddingModel();
    }
}
