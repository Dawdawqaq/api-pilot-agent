package com.dochelper.system.api.vo;

/**
 * 基础设施概览响应。
 *
 * @param application 应用名称
 * @param schemaVersion 数据库结构版本
 * @param qdrantCollection Qdrant Collection
 * @param objectStorageBucket MinIO Bucket
 * @param knowledgeEnabled 是否启用业务知识库
 * @param chatModel 当前对话模型名称
 */
public record InfrastructureOverviewResponse(
        String application,
        String schemaVersion,
        String qdrantCollection,
        String objectStorageBucket,
        boolean knowledgeEnabled,
        String chatModel
) {
}
