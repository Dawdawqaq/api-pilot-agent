package com.dochelper.model;

import com.dochelper.model.application.LlmConfigurationService.ResolvedConfiguration;
import com.dochelper.model.domain.LlmConfiguration;
import com.dochelper.common.exception.BusinessException;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.*;

/** 使用本地模拟服务验证真实协议、地址拼接、密钥注入和错误脱敏。 */
class RuntimeChatModelFactoryTest {
    private WireMockServer server;
    private final RuntimeChatModelFactory factory = new RuntimeChatModelFactory();
    @BeforeEach void setUp() { server = new WireMockServer(wireMockConfig().dynamicPort()); server.start(); }
    @AfterEach void tearDown() { server.stop(); }

    @Test void shouldCallCompatibleApiWithRootAndVersionedBaseUrls() {
        server.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(okJson("""
                {"id":"test","object":"chat.completion","created":1,"model":"mock-model",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"OK"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":2,"completion_tokens":1,"total_tokens":3}}
                """)));
        for (String suffix : java.util.List.of("", "/v1")) {
            var resolved = new ResolvedConfiguration(new LlmConfiguration("API", "OPENAI_COMPATIBLE",
                    server.baseUrl() + suffix, "mock-model", null), "private-key");
            assertThat(factory.create(resolved).call(new Prompt("Reply OK")).getResult().getOutput().getText()).isEqualTo("OK");
        }
        server.verify(2, postRequestedFor(urlEqualTo("/v1/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer private-key"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("mock-model")))
                .withRequestBody(notContaining("thinking")));
        server.stubFor(post(urlEqualTo("/compatible-mode/v1/chat/completions")).willReturn(okJson("""
                {"id":"test","object":"chat.completion","created":1,"model":"mock-model",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"OK"},"finish_reason":"stop"}]}
                """)));
        var prefixed = new ResolvedConfiguration(new LlmConfiguration("API", "DASHSCOPE",
                server.baseUrl() + "/compatible-mode", "mock-model", null), "private-key");
        assertThat(factory.create(prefixed).call(new Prompt("OK")).getResult().getOutput().getText()).isEqualTo("OK");
        server.verify(1, postRequestedFor(urlEqualTo("/compatible-mode/v1/chat/completions")));
    }

    @Test void shouldNotLeakProviderErrorBodyOrRetryRejectedRequests() {
        server.stubFor(post(anyUrl()).willReturn(aResponse().withStatus(401).withBody("provider echoed private-key")));
        var resolved = new ResolvedConfiguration(new LlmConfiguration("API", "OPENAI_COMPATIBLE", server.baseUrl(), "model", null), "private-key");
        assertThatThrownBy(() -> factory.create(resolved).call(new Prompt("OK")))
                .isInstanceOf(BusinessException.class).hasMessageNotContaining("private-key");
        server.verify(1, postRequestedFor(anyUrl()));
    }
}
