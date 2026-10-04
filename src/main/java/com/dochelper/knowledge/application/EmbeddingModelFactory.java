package com.dochelper.knowledge.application;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

import com.dochelper.common.exception.BusinessException;
import com.dochelper.common.exception.CommonErrorCode;
import com.dochelper.model.DeterministicEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestClient;

/** 为独立的嵌入供应商创建有超时、无自动重试的客户端。 */
@Component
public class EmbeddingModelFactory {
    public EmbeddingModel create(String mode, String baseUrl, String model, Integer dimensions, String key) {
        if ("DEVELOPMENT".equals(mode)) return new DeterministicEmbeddingModel();
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var requests = new JdkClientHttpRequestFactory(http);
        requests.setReadTimeout(Duration.ofSeconds(30));
        String path = URI.create(baseUrl).getPath();
        boolean versioned = path != null && path.matches(".*/v\\d+(?:beta\\d*)?(?:/.*)?");
        var api = OpenAiApi.builder().baseUrl(baseUrl).apiKey(key)
                .embeddingsPath(versioned ? "/embeddings" : "/v1/embeddings")
                .restClientBuilder(RestClient.builder().requestFactory(requests))
                .responseErrorHandler(new DefaultResponseErrorHandler() {
                    @Override public void handleError(URI url, org.springframework.http.HttpMethod method,
                            org.springframework.http.client.ClientHttpResponse response) {
                        throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT,
                                "嵌入服务拒绝请求，请检查地址、模型、维度及 API Key");
                    }
                }).build();
        var options = OpenAiEmbeddingOptions.builder().model(model);
        if (dimensions != null) options.dimensions(dimensions);
        return new OpenAiEmbeddingModel(api, MetadataMode.NONE, options.build(),
                RetryTemplate.builder().maxAttempts(1).fixedBackoff(1).build());
    }
}
