package com.dochelper.openapi.domain;

import java.time.LocalDateTime;

/**
 * OpenAPI 导入版本。
 *
 * @param id 导入标识
 * @param projectId 项目标识
 * @param revisionNumber 修订号
 * @param retryOfId 原失败导入标识
 * @param fileName 文件名
 * @param contentType 内容类型
 * @param contentHash 内容摘要
 * @param rawContent 原始文档
 * @param specificationVersion 规范版本
 * @param documentTitle 文档标题
 * @param documentVersion 文档版本
 * @param status 导入状态
 * @param errorMessage 失败原因
 * @param endpointCount 接口数量
 * @param schemaCount Schema 数量
 * @param securitySchemeCount 安全方案数量
 * @param createdAt 创建时间
 * @param completedAt 完成时间
 */
public record OpenApiImport(
        Long id,
        Long projectId,
        int revisionNumber,
        Long retryOfId,
        String fileName,
        String contentType,
        String contentHash,
        String rawContent,
        String specificationVersion,
        String documentTitle,
        String documentVersion,
        ImportStatus status,
        String errorMessage,
        int endpointCount,
        int schemaCount,
        int securitySchemeCount,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
