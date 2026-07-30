package com.dochelper.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 非 Spring 自动配置基础设施的探测端点。
 *
 * @param qdrantHealthEnabled 是否启用 Qdrant 健康探测
 * @param qdrantHttpUrl Qdrant HTTP 地址
 * @param qdrantCollection 项目当前使用的 Qdrant Collection
 */
@Validated
@ConfigurationProperties(prefix = "dochelper.infrastructure")
public record InfrastructureEndpointProperties(
        boolean qdrantHealthEnabled,
        @NotBlank String qdrantHttpUrl,
        @NotBlank String qdrantCollection
) {
}
