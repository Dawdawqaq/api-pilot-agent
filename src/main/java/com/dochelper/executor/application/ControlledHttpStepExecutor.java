package com.dochelper.executor.application;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.dochelper.common.exception.BusinessException;
import com.dochelper.executor.api.dto.AuthenticationRequest;
import com.dochelper.executor.api.dto.ExecutionStepRequest;
import com.dochelper.executor.config.ExecutorProperties;
import com.dochelper.executor.domain.ApiKeyLocation;
import com.dochelper.executor.domain.AuthenticationType;
import com.dochelper.executor.domain.HttpExchangeResult;
import com.dochelper.executor.exception.ExecutionErrorCode;
import com.dochelper.project.domain.ProjectEnvironment;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutException;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.util.UriComponentsBuilder;

import reactor.netty.http.client.HttpClient;

/**
 * 使用 WebClient 执行经过策略校验的单个 HTTP 请求。
 */
@Component
public class ControlledHttpStepExecutor {

    private static final int MAX_HEADERS = 50;
    private static final int MAX_QUERY_PARAMS = 50;

    private final VariableTemplateRenderer renderer;
    private final TargetAccessPolicy accessPolicy;
    private final ExecutorProperties properties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public ControlledHttpStepExecutor(
            VariableTemplateRenderer renderer,
            TargetAccessPolicy accessPolicy,
            ExecutorProperties properties,
            ObjectMapper objectMapper
    ) {
        this.renderer = renderer;
        this.accessPolicy = accessPolicy;
        this.properties = properties;
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.create()
                // 重试统一由业务策略决策，禁止底层在连接中断时透明重发写请求。
                .disableRetry(true)
                .option(
                        ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        Math.toIntExact(properties.connectTimeout().toMillis())
                )
                .responseTimeout(properties.responseTimeout())
                .followRedirect(false);
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(properties.maxResponseSizeBytes()))
                .build();
        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .build();
    }

    public HttpExchangeResult execute(
            ProjectEnvironment environment,
            ExecutionStepRequest step,
            Map<String, Object> variables
    ) {
        validateCollectionSizes(step);
        String method = accessPolicy.validateAndNormalizeMethod(environment, step);
        PreparedRequest prepared = prepare(environment, step, variables, method);
        accessPolicy.validateTarget(environment, prepared.uri());

        long startedAt = System.nanoTime();
        try {
            WebClient.RequestBodySpec request = webClient.method(HttpMethod.valueOf(method))
                    .uri(prepared.uri())
                    .headers(headers -> prepared.headers().forEach(headers::set));
            WebClient.RequestHeadersSpec<?> requestSpec;
            if (prepared.body() == null || prepared.body().isNull()) {
                requestSpec = request;
            } else {
                requestSpec = request.contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(prepared.body());
            }
            HttpExchangeResult result = requestSpec.exchangeToMono(response ->
                            response.bodyToMono(byte[].class)
                                    .defaultIfEmpty(new byte[0])
                                    .map(bytes -> toResult(
                                            prepared,
                                            method,
                                            response.statusCode().value(),
                                            response.headers().asHttpHeaders(),
                                            bytes,
                                            elapsedMillis(startedAt)
                                    )))
                    .block(properties.responseTimeout().plusSeconds(1));
            if (result == null) {
                throw new BusinessException(ExecutionErrorCode.REMOTE_REQUEST_FAILED);
            }
            return result;
        } catch (BusinessException exception) {
            throw exception;
        } catch (DataBufferLimitException exception) {
            throw new BusinessException(ExecutionErrorCode.RESPONSE_TOO_LARGE);
        } catch (WebClientRequestException exception) {
            if (hasCause(exception, ReadTimeoutException.class)
                    || hasCause(exception, TimeoutException.class)) {
                throw new BusinessException(ExecutionErrorCode.REQUEST_TIMEOUT);
            }
            throw new BusinessException(
                    ExecutionErrorCode.REMOTE_REQUEST_FAILED,
                    "被测接口连接失败：" + exception.getClass().getSimpleName()
            );
        } catch (IllegalStateException exception) {
            if (hasCause(exception, TimeoutException.class)
                    || hasCause(exception, DataBufferLimitException.class)) {
                throw new BusinessException(
                        hasCause(exception, DataBufferLimitException.class)
                                ? ExecutionErrorCode.RESPONSE_TOO_LARGE
                                : ExecutionErrorCode.REQUEST_TIMEOUT
                );
            }
            throw exception;
        }
    }

    private PreparedRequest prepare(
            ProjectEnvironment environment,
            ExecutionStepRequest step,
            Map<String, Object> variables,
            String method
    ) {
        String path = renderer.render(step.path(), variables);
        accessPolicy.validateRelativePath(path);
        Map<String, String> pathVariables = renderer.renderMap(step.pathVariables(), variables);
        Map<String, String> queryParams = renderer.renderMap(step.queryParams(), variables);
        Map<String, String> headers = renderer.renderMap(step.headers(), variables);
        JsonNode body = renderer.renderJson(step.body(), variables);
        applyAuthentication(step.authentication(), variables, queryParams, headers);
        validateRequestBody(body);
        try {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromUriString(environment.baseUrl())
                    .path(path);
            queryParams.forEach(builder::queryParam);
            URI uri = builder.buildAndExpand(pathVariables).encode().toUri();
            return new PreparedRequest(uri, headers, body, method);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ExecutionErrorCode.INVALID_STEP,
                    "路径变量不完整或请求地址无效"
            );
        }
    }

    private void applyAuthentication(
            AuthenticationRequest authentication,
            Map<String, Object> variables,
            Map<String, String> queryParams,
            Map<String, String> headers
    ) {
        AuthenticationType type = authentication == null || authentication.type() == null
                ? AuthenticationType.NONE
                : authentication.type();
        switch (type) {
            case NONE -> {
            }
            case BEARER -> {
                String token = requireText(
                        renderer.render(authentication.token(), variables),
                        "Bearer Token"
                );
                headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }
            case BASIC -> {
                String username = requireText(
                        renderer.render(authentication.username(), variables),
                        "Basic 用户名"
                );
                String password = requireText(
                        renderer.render(authentication.password(), variables),
                        "Basic 密码"
                );
                String encoded = Base64.getEncoder().encodeToString(
                        (username + ":" + password).getBytes(StandardCharsets.UTF_8)
                );
                headers.put(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
            }
            case API_KEY -> {
                String name = requireText(
                        renderer.render(authentication.apiKeyName(), variables),
                        "API Key 名称"
                );
                String value = requireText(
                        renderer.render(authentication.apiKeyValue(), variables),
                        "API Key 值"
                );
                if (authentication.apiKeyLocation() == ApiKeyLocation.QUERY) {
                    queryParams.put(name, value);
                } else {
                    headers.put(name, value);
                }
            }
        }
    }

    private HttpExchangeResult toResult(
            PreparedRequest prepared,
            String method,
            int statusCode,
            HttpHeaders responseHeaders,
            byte[] bytes,
            long durationMs
    ) {
        if (bytes.length > properties.maxResponseSizeBytes()) {
            throw new BusinessException(ExecutionErrorCode.RESPONSE_TOO_LARGE);
        }
        String rawBody = new String(bytes, StandardCharsets.UTF_8);
        JsonNode body = parseResponseBody(rawBody);
        Map<String, List<String>> headers = new LinkedHashMap<>();
        responseHeaders.forEach((name, values) -> headers.put(name, List.copyOf(values)));
        return new HttpExchangeResult(
                prepared.uri().toString(),
                method,
                Map.copyOf(prepared.headers()),
                prepared.body(),
                statusCode,
                Map.copyOf(headers),
                body,
                rawBody,
                durationMs
        );
    }

    private JsonNode parseResponseBody(String rawBody) {
        if (rawBody.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(rawBody);
        } catch (JsonProcessingException exception) {
            return TextNode.valueOf(rawBody);
        }
    }

    private void validateRequestBody(JsonNode body) {
        if (body == null || body.isNull()) {
            return;
        }
        try {
            int length = objectMapper.writeValueAsBytes(body).length;
            if (length > properties.maxRequestSizeBytes()) {
                throw new BusinessException(ExecutionErrorCode.REQUEST_TOO_LARGE);
            }
        } catch (JsonProcessingException exception) {
            throw new BusinessException(
                    ExecutionErrorCode.INVALID_STEP,
                    "请求体无法序列化为 JSON"
            );
        }
    }

    private void validateCollectionSizes(ExecutionStepRequest step) {
        if (size(step.headers()) > MAX_HEADERS || size(step.queryParams()) > MAX_QUERY_PARAMS) {
            throw new BusinessException(
                    ExecutionErrorCode.INVALID_STEP,
                    "单步请求头或查询参数数量超过限制"
            );
        }
    }

    private int size(Map<?, ?> values) {
        return values == null ? 0 : values.size();
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(
                    ExecutionErrorCode.INVALID_STEP,
                    fieldName + " 不能为空"
            );
        }
        return value;
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> expected) {
        Throwable current = throwable;
        while (current != null) {
            if (expected.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private record PreparedRequest(
            URI uri,
            Map<String, String> headers,
            JsonNode body,
            String method
    ) {
    }
}
