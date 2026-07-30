package com.dochelper.model;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * 无外部 Embedding 服务时使用的确定性本地向量模型。
 *
 * <p>该实现只用于开发和可重复测试，不能作为真实语义向量效果指标。</p>
 */
public final class DeterministicEmbeddingModel implements EmbeddingModel {

    private static final int DIMENSIONS = 4;

    /**
     * 将文本映射为可预测的单位向量。
     *
     * @param request 向量化请求
     * @return 向量化响应
     */
    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> instructions = request.getInstructions();
        for (int index = 0; index < instructions.size(); index++) {
            embeddings.add(new Embedding(vectorFor(instructions.get(index)), index));
        }
        return new EmbeddingResponse(embeddings);
    }

    /**
     * 返回固定向量维度。
     *
     * @return 向量维度
     */
    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    /**
     * 将单个文档映射为确定性向量。
     *
     * @param document 待向量化文档
     * @return 文档向量
     */
    @Override
    public float[] embed(Document document) {
        return vectorFor(document.getText());
    }

    private float[] vectorFor(String text) {
        if (text.contains("登录") || text.contains("认证")) {
            return new float[]{1.0F, 0.0F, 0.0F, 0.0F};
        }
        if (text.contains("订单") || text.contains("支付")) {
            return new float[]{0.0F, 1.0F, 0.0F, 0.0F};
        }
        if (text.contains("用户") || text.contains("账号")) {
            return new float[]{0.0F, 0.0F, 1.0F, 0.0F};
        }
        return new float[]{0.0F, 0.0F, 0.0F, 1.0F};
    }
}
