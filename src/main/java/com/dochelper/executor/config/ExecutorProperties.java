package com.dochelper.executor.config;

import java.time.Duration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 受控 HTTP 执行器资源限制配置。
 */
@Validated
@ConfigurationProperties(prefix = "dochelper.executor")
public record ExecutorProperties(
        @NotNull Duration connectTimeout,
        @NotNull Duration responseTimeout,
        @Min(1024) @Max(10 * 1024 * 1024) int maxResponseSizeBytes,
        @Min(0) @Max(5 * 1024 * 1024) int maxRequestSizeBytes,
        @Min(1) @Max(50) int maxSteps,
        @Min(0) @Max(5) int maxStepRetries,
        @NotNull Duration retryBackoff,
        @NotBlank String sensitiveNamePattern
) {
}
