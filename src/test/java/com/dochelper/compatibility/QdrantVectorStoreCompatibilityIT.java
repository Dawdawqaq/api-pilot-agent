package com.dochelper.compatibility;

import java.util.List;

import com.dochelper.model.DeterministicEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.ActiveProfiles;

import jakarta.annotation.Resource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Spring AI Qdrant VectorStore 的真实读写兼容性。
 */
@SpringBootTest(
        classes = QdrantVectorStoreCompatibilityIT.QdrantTestApplication.class,
        properties = {
                "spring.main.web-application-type=none",
                "spring.ai.vectorstore.qdrant.host=localhost",
                "spring.ai.vectorstore.qdrant.port=6334",
                "spring.ai.vectorstore.qdrant.use-tls=false",
                "spring.ai.vectorstore.qdrant.collection-name=dochelper_stage0",
                "spring.ai.vectorstore.qdrant.initialize-schema=true"
        }
)
@ActiveProfiles("test-qdrant")
@Import(QdrantVectorStoreCompatibilityIT.EmbeddingTestConfiguration.class)
class QdrantVectorStoreCompatibilityIT {

    @Resource
    private VectorStore vectorStore;

    /**
     * 仅启动 Qdrant 自动配置，避免兼容性测试加载业务数据库组件。
     */
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class QdrantTestApplication {
    }

    /**
     * 验证文档能够写入 Qdrant，并按确定性向量召回。
     */
    @Test
    void shouldWriteAndSearchDocuments() {
        Document loginDocument = new Document("登录接口通过用户名和密码签发访问令牌");
        Document orderDocument = new Document("订单接口负责创建订单并完成支付");
        vectorStore.add(List.of(loginDocument, orderDocument));

        List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
                .query("如何进行用户认证登录")
                .topK(1)
                .similarityThreshold(0.0)
                .build());

        assertThat(results)
                .hasSize(1)
                .first()
                .extracting(Document::getText)
                .asString()
                .contains("登录接口");

        vectorStore.delete(List.of(loginDocument.getId(), orderDocument.getId()));
    }

    /**
     * 提供不访问外部服务的确定性向量模型。
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class EmbeddingTestConfiguration {

        /**
         * 创建优先使用的测试向量模型。
         *
         * @return 确定性向量模型
         */
        @Bean
        @Primary
        EmbeddingModel deterministicEmbeddingModel() {
            return new DeterministicEmbeddingModel();
        }
    }
}
