package com.dochelper.system.api.vo;

/**
 * 基础设施概览响应。
 *
 * @param application 应用名称
 * @param schemaVersion 数据库结构版本
 * @param redisNamespace Redis Key 命名空间
 * @param qdrantCollection Qdrant Collection
 * @param objectStorageBucket MinIO Bucket
 */
public record InfrastructureOverviewResponse(
        String application,
        String schemaVersion,
        String redisNamespace,
        String qdrantCollection,
        String objectStorageBucket
) {
}
