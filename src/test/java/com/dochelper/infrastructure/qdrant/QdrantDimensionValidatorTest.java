package com.dochelper.infrastructure.qdrant;

import com.dochelper.infrastructure.config.InfrastructureEndpointProperties;
import com.dochelper.model.DeterministicEmbeddingModel;
import com.dochelper.model.ModelProviderProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 Qdrant 向量维度校验器。
 */
class QdrantDimensionValidatorTest {

    private WireMockServer wireMock;
    private ObjectProvider<org.springframework.ai.embedding.EmbeddingModel> embeddingModelProvider;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        embeddingModelProvider = mock(ObjectProvider.class);
        when(embeddingModelProvider.getIfAvailable()).thenReturn(new DeterministicEmbeddingModel());
    }

    @AfterEach
    void tearDown() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @Test
    void shouldReturnMatchedWhenVectorSizeMatchesExpected() {
        wireMock.stubFor(get(urlEqualTo("/collections/test_collection"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "result": {
                                    "status": "green",
                                    "config": {
                                      "params": {
                                        "vectors": {
                                          "size": 4,
                                          "distance": "Cosine"
                                        }
                                      }
                                    }
                                  }
                                }
                                """)));

        QdrantDimensionValidator validator = createValidator(null);

        StepVerifier.create(validator.validate())
                .assertNext(result -> {
                    assertThat(result.matched()).isTrue();
                    assertThat(result.actualDimensions()).isEqualTo(4);
                    assertThat(result.expectedDimensions()).isEqualTo(4);
                    assertThat(result.collectionExists()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnMismatchWhenVectorSizeDiffersFromExpected() {
        wireMock.stubFor(get(urlEqualTo("/collections/test_collection"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "result": {
                                    "status": "green",
                                    "config": {
                                      "params": {
                                        "vectors": {
                                          "size": 4,
                                          "distance": "Cosine"
                                        }
                                      }
                                    }
                                  }
                                }
                                """)));

        // 配置了 1536 维真实模型，但服务端仍是 4 维
        QdrantDimensionValidator validator = createValidator(1536);

        StepVerifier.create(validator.validate())
                .assertNext(result -> {
                    assertThat(result.matched()).isFalse();
                    assertThat(result.actualDimensions()).isEqualTo(4);
                    assertThat(result.expectedDimensions()).isEqualTo(1536);
                    assertThat(result.collectionExists()).isTrue();
                    assertThat(result.message()).contains("维度不匹配");
                })
                .verifyComplete();

        StepVerifier.create(validator.assertDimensionMatch())
                .expectErrorMatches(throwable -> throwable instanceof IllegalStateException
                        && throwable.getMessage().contains("Qdrant 向量维度不匹配错误"))
                .verify();
    }

    @Test
    void shouldHandleCollectionNotFound() {
        wireMock.stubFor(get(urlEqualTo("/collections/test_collection"))
                .willReturn(aResponse().withStatus(404)));

        QdrantDimensionValidator validator = createValidator(1024);

        StepVerifier.create(validator.validate())
                .assertNext(result -> {
                    assertThat(result.matched()).isFalse();
                    assertThat(result.collectionExists()).isFalse();
                    assertThat(result.message()).contains("尚未创建");
                })
                .verifyComplete();

        StepVerifier.create(validator.assertDimensionMatch())
                .verifyComplete(); // 不存在的集合允许跳过断言由初始化创建
    }

    private QdrantDimensionValidator createValidator(Integer configuredDimensions) {
        InfrastructureEndpointProperties infraProps = new InfrastructureEndpointProperties(
                true, wireMock.baseUrl(), "test_collection"
        );
        ModelProviderProperties modelProps = new ModelProviderProperties(
                "http://dummy", "dummy-key", "gpt-4o", null, null, "test-model", configuredDimensions
        );
        return new QdrantDimensionValidator(
                WebClient.builder(), infraProps, embeddingModelProvider, modelProps
        );
    }
}
