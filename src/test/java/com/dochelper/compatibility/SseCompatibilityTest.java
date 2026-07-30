package com.dochelper.compatibility;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 WebFlux SSE 接口的兼容性。
 */
@ActiveProfiles("test")
@WebFluxTest(controllers = CompatibilityStreamController.class)
class SseCompatibilityTest {

    @Autowired
    private WebTestClient webTestClient;

    /**
     * 验证客户端可以收到完整的 Agent 生命周期事件流。
     */
    @Test
    void shouldStreamAgentLifecycleEvents() {
        String responseBody = webTestClient
                .get()
                .uri("/api/compatibility/stream")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith("text/event-stream")
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(responseBody)
                .contains("PLANNING")
                .contains("EXECUTING")
                .contains("SUCCEEDED");
    }
}
