package com.dochelper.executor.application;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.dochelper.executor.api.dto.AuthenticationRequest;
import com.dochelper.executor.api.dto.ExecutionStepRequest;
import com.dochelper.executor.config.ExecutorProperties;
import com.dochelper.executor.domain.ApiKeyLocation;
import com.dochelper.executor.domain.AuthenticationType;
import com.dochelper.executor.domain.HttpExchangeResult;
import com.dochelper.project.domain.ProjectEnvironment;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 使用 WireMock 验证 WebClient 请求渲染、认证注入和响应读取。
 */
class ControlledHttpStepExecutorTest {

    private WireMockServer wireMock;
    private ControlledHttpStepExecutor executor;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        ExecutorProperties properties = new ExecutorProperties(
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                1024 * 1024,
                512 * 1024,
                10,
                "(?i).*(authorization|token|password|secret|cookie|api[-_]?key).*"
        );
        executor = new ControlledHttpStepExecutor(
                new VariableTemplateRenderer(JsonMapper.builder().build()),
                new TargetAccessPolicy(),
                properties,
                JsonMapper.builder().build()
        );
    }

    @AfterEach
    void tearDown() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @Test
    void shouldRenderPathQueryAndBearerAuthentication() {
        wireMock.stubFor(get(urlPathEqualTo("/users/7"))
                .withQueryParam("verbose", equalTo("true"))
                .withHeader("Authorization", equalTo("Bearer access-123"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":7,\"name\":\"tester\"}")));
        ExecutionStepRequest step = new ExecutionStepRequest(
                "查询用户",
                "GET",
                "/users/{userId}",
                Map.of("userId", "{{id}}"),
                Map.of("verbose", "true"),
                Map.of(),
                null,
                new AuthenticationRequest(
                        AuthenticationType.BEARER,
                        "{{accessToken}}",
                        null,
                        null,
                        null,
                        null,
                        ApiKeyLocation.HEADER
                ),
                List.of(),
                List.of(),
                false
        );

        HttpExchangeResult result = executor.execute(
                environment(),
                step,
                Map.of("id", 7, "accessToken", "access-123")
        );

        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.responseBody().path("id").asInt()).isEqualTo(7);
        wireMock.verify(getRequestedFor(urlPathEqualTo("/users/7")));
    }

    private ProjectEnvironment environment() {
        return new ProjectEnvironment(
                1L,
                1L,
                "wiremock",
                wireMock.baseUrl(),
                "GET,POST,PUT,PATCH,DELETE",
                true,
                true,
                null,
                null
        );
    }
}
