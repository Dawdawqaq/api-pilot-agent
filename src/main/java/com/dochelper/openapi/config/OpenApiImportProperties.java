package com.dochelper.openapi.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * OpenAPI 导入配置。
 *
 * @param maxFileSizeBytes 单个文件最大字节数
 */
@Validated
@ConfigurationProperties(prefix = "dochelper.openapi")
public record OpenApiImportProperties(
        @Min(1024) @Max(20 * 1024 * 1024) int maxFileSizeBytes
) {
}
