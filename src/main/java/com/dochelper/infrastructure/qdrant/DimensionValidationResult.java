package com.dochelper.infrastructure.qdrant;

/**
 * Qdrant 向量集合维度校验结果。
 *
 * @param collection 集合名称
 * @param actualDimensions Qdrant 集合实际向量维度（-1 表示未知或集合不存在）
 * @param expectedDimensions 当前 Embedding 模型预期向量维度
 * @param matched 维度是否匹配
 * @param collectionExists 集合是否存在
 * @param message 诊断描述信息
 */
public record DimensionValidationResult(
        String collection,
        int actualDimensions,
        int expectedDimensions,
        boolean matched,
        boolean collectionExists,
        String message
) {

    public static DimensionValidationResult success(String collection, int dimensions) {
        return new DimensionValidationResult(
                collection, dimensions, dimensions, true, true,
                "Qdrant 向量集合维度匹配（" + dimensions + " 维）"
        );
    }

    public static DimensionValidationResult mismatch(
            String collection,
            int actualDimensions,
            int expectedDimensions
    ) {
        return new DimensionValidationResult(
                collection, actualDimensions, expectedDimensions, false, true,
                "Qdrant 向量集合维度不匹配：实际集合为 " + actualDimensions
                        + " 维，当前 Embedding 模型预期为 " + expectedDimensions + " 维"
        );
    }

    public static DimensionValidationResult notFound(String collection, int expectedDimensions) {
        return new DimensionValidationResult(
                collection, -1, expectedDimensions, false, false,
                "Qdrant 向量集合 '" + collection + "' 尚未创建"
        );
    }

    public static DimensionValidationResult error(String collection, int expectedDimensions, String error) {
        return new DimensionValidationResult(
                collection, -1, expectedDimensions, false, false,
                "Qdrant 维度校验失败：" + error
        );
    }
}
