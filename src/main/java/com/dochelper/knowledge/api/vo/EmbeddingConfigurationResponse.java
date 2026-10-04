package com.dochelper.knowledge.api.vo;

/** 当前生效的向量配置；开发向量不能代表真实语义检索效果。 */
public record EmbeddingConfigurationResponse(boolean enabled, String mode, String baseUrl, String model,
        int dimensions, Integer requestedDimensions, String collection, boolean apiKeyConfigured, boolean persistentStorageReady,
        boolean rebuilding, boolean rebuildRequired, int unusedCollections,
        long indexedDocuments, long indexedChunks, String message) { }
