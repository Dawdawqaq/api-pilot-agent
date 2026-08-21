package com.dochelper.infrastructure.health;

import com.fasterxml.jackson.databind.JsonNode;
import com.dochelper.infrastructure.config.InfrastructureEndpointProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

import com.dochelper.infrastructure.qdrant.QdrantDimensionValidator;

/**
 * 通过 Qdrant HTTP 接口检查项目专用 Collection 的健康状态及向量维度一致性。
 */
@Component("qdrant")
@ConditionalOnProperty(
        prefix = "dochelper.infrastructure",
        name = "qdrant-health-enabled",
        havingValue = "true"
)
public class QdrantHealthIndicator implements ReactiveHealthIndicator {

    private final WebClient webClient;
    private final String collection;
    private final QdrantDimensionValidator dimensionValidator;

    /**
     * 创建 Qdrant 健康检查器。
     *
     * @param webClientBuilder WebClient 构建器
     * @param properties 基础设施端点配置
     * @param dimensionValidator 维度校验器
     */
    public QdrantHealthIndicator(
            WebClient.Builder webClientBuilder,
            InfrastructureEndpointProperties properties,
            QdrantDimensionValidator dimensionValidator
    ) {
        this.webClient = webClientBuilder.baseUrl(properties.qdrantHttpUrl()).build();
        this.collection = properties.qdrantCollection();
        this.dimensionValidator = dimensionValidator;
    }

    @Override
    public Mono<Health> health() {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment("collections", collection)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(response -> dimensionValidator.validate().map(validation -> {
                    String collectionStatus = response.path("result").path("status").asText("unknown");
                    boolean statusGreen = "green".equalsIgnoreCase(collectionStatus);
                    boolean isHealthy = statusGreen && validation.matched();
                    Health.Builder healthBuilder = isHealthy ? Health.up() : Health.down();
                    return healthBuilder
                            .withDetail("collection", collection)
                            .withDetail("collectionStatus", collectionStatus)
                            .withDetail("actualDimensions", validation.actualDimensions())
                            .withDetail("expectedDimensions", validation.expectedDimensions())
                            .withDetail("dimensionMatched", validation.matched())
                            .withDetail("dimensionMessage", validation.message())
                            .build();
                }))
                .onErrorResume(exception -> Mono.just(
                        Health.down(exception)
                                .withDetail("collection", collection)
                                .build()
                ));
    }
}
