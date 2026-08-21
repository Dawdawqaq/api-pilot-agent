package com.dochelper.infrastructure.qdrant;

import com.fasterxml.jackson.databind.JsonNode;
import com.dochelper.infrastructure.config.InfrastructureEndpointProperties;
import com.dochelper.model.DeterministicEmbeddingModel;
import com.dochelper.model.ModelProviderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * 校验当前 Qdrant Collection 向量维度与 Embedding 模型的一致性。
 */
@Component
public class QdrantDimensionValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(QdrantDimensionValidator.class);

    private final WebClient webClient;
    private final String collection;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final ModelProviderProperties modelProperties;
    private volatile Integer cachedExpectedDimensions;

    public QdrantDimensionValidator(
            WebClient.Builder webClientBuilder,
            InfrastructureEndpointProperties infrastructureProperties,
            ObjectProvider<EmbeddingModel> embeddingModelProvider,
            ModelProviderProperties modelProperties
    ) {
        this.webClient = webClientBuilder.baseUrl(infrastructureProperties.qdrantHttpUrl()).build();
        this.collection = infrastructureProperties.qdrantCollection();
        this.embeddingModelProvider = embeddingModelProvider;
        this.modelProperties = modelProperties;
    }

    /**
     * 异步校验 Qdrant 集合维度是否与当前 Embedding 模型匹配。
     *
     * @return 校验诊断结果
     */
    public Mono<DimensionValidationResult> validate() {
        int expected = resolveExpectedDimensions();
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.pathSegment("collections", collection).build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> {
                    int actual = parseVectorSize(response);
                    if (actual <= 0) {
                        return DimensionValidationResult.error(
                                collection, expected, "未能从 Qdrant 响应中解析出有效的向量维度"
                        );
                    }
                    if (actual == expected) {
                        return DimensionValidationResult.success(collection, actual);
                    }
                    return DimensionValidationResult.mismatch(collection, actual, expected);
                })
                .onErrorResume(WebClientResponseException.NotFound.class, notFound ->
                        Mono.just(DimensionValidationResult.notFound(collection, expected)))
                .onErrorResume(exception -> {
                    LOGGER.warn("Qdrant 维度校验调用异常：{}", exception.getMessage());
                    return Mono.just(DimensionValidationResult.error(
                            collection, expected, exception.getMessage()
                    ));
                });
    }

    /**
     * 断言维度匹配，若不匹配或校验异常则抛出 IllegalStateException。
     *
     * @return 响应式完成信号
     */
    public Mono<Void> assertDimensionMatch() {
        return validate().flatMap(result -> {
            if (result.matched()) {
                return Mono.empty();
            }
            if (!result.collectionExists()) {
                LOGGER.info("Qdrant 向量集合 '{}' 尚不存在，跳过维度断言（将由初始化流程创建）", collection);
                return Mono.empty();
            }
            return Mono.error(new IllegalStateException(
                    "Qdrant 向量维度不匹配错误：" + result.message()
                            + "。请清空旧集合并重新执行数据索引，或校准 dochelper.ai 配置。"
            ));
        });
    }

    /**
     * 获取当前系统预期的向量维度大小。
     *
     * @return 预期向量维度
     */
    public int resolveExpectedDimensions() {
        if (cachedExpectedDimensions != null) {
            return cachedExpectedDimensions;
        }
        if (modelProperties.embeddingDimensions() != null && modelProperties.embeddingDimensions() > 0) {
            cachedExpectedDimensions = modelProperties.embeddingDimensions();
            return cachedExpectedDimensions;
        }
        EmbeddingModel model = embeddingModelProvider.getIfAvailable();
        if (model == null || model instanceof DeterministicEmbeddingModel) {
            cachedExpectedDimensions = 4;
            return cachedExpectedDimensions;
        }
        try {
            // 通过对探测词生成单次向量推断其输出维度
            float[] probe = model.embed("probe");
            if (probe != null && probe.length > 0) {
                cachedExpectedDimensions = probe.length;
                return cachedExpectedDimensions;
            }
        } catch (Exception exception) {
            LOGGER.warn("通过探测请求获取 Embedding 维度失败，使用默认 1536 维", exception);
        }
        cachedExpectedDimensions = 1536;
        return cachedExpectedDimensions;
    }

    private int parseVectorSize(JsonNode response) {
        JsonNode vectorsNode = response.path("result").path("config").path("params").path("vectors");
        if (vectorsNode.isMissingNode() || vectorsNode.isNull()) {
            return -1;
        }
        if (vectorsNode.has("size")) {
            return vectorsNode.path("size").asInt(-1);
        }
        if (vectorsNode.isObject() && vectorsNode.fieldNames().hasNext()) {
            String firstFieldName = vectorsNode.fieldNames().next();
            return vectorsNode.path(firstFieldName).path("size").asInt(-1);
        }
        return -1;
    }
}
