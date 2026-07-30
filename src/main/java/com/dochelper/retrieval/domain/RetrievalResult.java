package com.dochelper.retrieval.domain;

/**
 * 混合检索结果及来源引用。
 */
public record RetrievalResult(
        Long chunkId,
        Long documentId,
        String sourceName,
        String section,
        int chunkIndex,
        String content,
        double fusedScore,
        Integer keywordRank,
        Integer vectorRank,
        String citation
) {
}
