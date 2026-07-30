package com.dochelper.knowledge.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 知识库处理与检索配置。
 */
@Validated
@ConfigurationProperties(prefix = "dochelper.knowledge")
public record KnowledgeProperties(
        @Min(1024) @Max(50 * 1024 * 1024) int maxFileSizeBytes,
        @Min(1000) int maxExtractedCharacters,
        @Min(200) @Max(4000) int chunkSize,
        @Min(0) @Max(1000) int chunkOverlap,
        @Min(5) @Max(100) int retrievalCandidateSize,
        @Min(1) int rrfRankConstant
) {

    public KnowledgeProperties {
        if (chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("切片重叠长度必须小于切片长度");
        }
    }
}
