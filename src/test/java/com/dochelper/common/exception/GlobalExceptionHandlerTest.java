package com.dochelper.common.exception;

import com.dochelper.common.web.RequestIdWebFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证参数校验、统一异常响应和请求追踪标识。
 */
class GlobalExceptionHandlerTest {

    private final WebTestClient webTestClient = WebTestClient
            .bindToController(new ValidationTestController())
            .controllerAdvice(new GlobalExceptionHandler())
            .webFilter(new RequestIdWebFilter())
            .build();

    /**
     * 验证非法请求体返回稳定错误码和追踪标识。
     */
    @Test
    void shouldReturnUnifiedValidationError() {
        webTestClient.post()
                .uri("/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name": ""}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().exists(RequestIdWebFilter.REQUEST_ID_HEADER)
                .expectBody()
                .jsonPath("$.code").isEqualTo(CommonErrorCode.INVALID_ARGUMENT.code())
                .jsonPath("$.message").value(message ->
                        assertThat(message.toString()).contains("name")
                )
                .jsonPath("$.requestId").isNotEmpty();
    }

    /**
     * 参数校验测试接口，仅存在于测试上下文。
     */
    @RestController
    @RequestMapping("/test")
    static class ValidationTestController {

        /**
         * 接收需要校验的测试请求。
         *
         * @param request 测试请求
         */
        @PostMapping("/validation")
        void validate(@Valid @RequestBody ValidationRequest request) {
        }
    }

    /**
     * 参数校验测试请求。
     *
     * @param name 必填名称
     */
    record ValidationRequest(@NotBlank String name) {
    }
}
