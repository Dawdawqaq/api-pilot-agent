package com.dochelper.infrastructure.config;

import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Redis Key 命名空间配置。
 *
 * @param namespace 项目级 Key 前缀
 */
@Validated
@ConfigurationProperties(prefix = "dochelper.redis")
public record RedisNamespaceProperties(
        @Pattern(regexp = "^[a-z0-9-]+:$", message = "Redis 命名空间必须由小写字母、数字、短横线组成并以冒号结尾")
        String namespace
) {
}
