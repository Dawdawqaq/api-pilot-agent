package com.dochelper.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * MinIO 对象存储配置。
 *
 * @param enabled 是否启用对象存储
 * @param endpoint S3 服务端点
 * @param accessKey 访问账号
 * @param secretKey 访问密钥
 * @param bucket 项目专用 Bucket
 * @param verifyOnStartup 是否在启动时校验 Bucket
 */
@Validated
@ConfigurationProperties(prefix = "dochelper.storage")
public record ObjectStorageProperties(
        boolean enabled,
        @NotBlank String endpoint,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        @NotBlank String bucket,
        boolean verifyOnStartup
) {
}
