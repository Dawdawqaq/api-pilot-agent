package com.dochelper.model;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import com.dochelper.model.application.LlmConfigurationService.ResolvedConfiguration;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import com.dochelper.common.exception.BusinessException;
import com.dochelper.agent.exception.AgentErrorCode;

/** 构造有超时边界的 OpenAI 兼容对话模型，供应商扩展参数仅发送给对应供应商。 */
@Component
public class RuntimeChatModelFactory {
    public ChatModel create(ResolvedConfiguration resolved) {
        var config = resolved.config();
        var http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
        var requests = new JdkClientHttpRequestFactory(http);
        requests.setReadTimeout(Duration.ofSeconds(30));
        String path = URI.create(config.baseUrl()).getPath();
        boolean includesVersion = path != null && path.matches(".*/v\\d+(?:beta\\d*)?(?:/.*)?");
        OpenAiApi api = OpenAiApi.builder().baseUrl(config.baseUrl()).apiKey(resolved.apiKey())
                .completionsPath(includesVersion ? "/chat/completions" : "/v1/chat/completions")
                .responseErrorHandler(new DefaultResponseErrorHandler() {
                    @Override
                    public void handleError(URI url, HttpMethod method, ClientHttpResponse response) {
                        // 不记录供应商错误响应体，避免异常日志包含供应商回显的敏感值。
                        throw new BusinessException(AgentErrorCode.PLANNING_FAILED, "模型服务拒绝请求，请检查 LLM API 配置");
                    }
                })
                .restClientBuilder(RestClient.builder().requestFactory(requests)).build();
        var options = OpenAiChatOptions.builder().model(config.model()).temperature(0.1)
                .maxTokens(4096).parallelToolCalls(false);
        if ("DEEPSEEK".equals(config.provider())) {
            options.extraBody(Map.of("thinking", Map.of("type", "disabled")));
        }
        return OpenAiChatModel.builder().openAiApi(api).defaultOptions(options.build())
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).fixedBackoff(1).build()).build();
    }
}
