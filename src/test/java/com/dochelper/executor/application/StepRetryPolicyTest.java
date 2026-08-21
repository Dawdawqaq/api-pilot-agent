package com.dochelper.executor.application;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.dochelper.executor.api.dto.ExecutionStepRequest;
import com.dochelper.executor.config.ExecutorProperties;
import com.dochelper.executor.exception.ExecutionErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证步骤重试只覆盖可恢复错误和具备幂等保证的请求。
 */
class StepRetryPolicyTest {

    private final StepRetryPolicy policy = new StepRetryPolicy(new ExecutorProperties(
            Duration.ofSeconds(1), Duration.ofSeconds(2), 1024 * 1024, 512 * 1024,
            10, 2, Duration.ofMillis(1), "(?i).*(token|password|secret).*"
    ));

    @Test
    void shouldRetryIdempotentReadAfterTimeoutWithinAttemptLimit() {
        assertThat(policy.shouldRetry(
                step("GET", Map.of()), ExecutionErrorCode.REQUEST_TIMEOUT.code(), 1
        )).isTrue();
        assertThat(policy.shouldRetry(
                step("GET", Map.of()), ExecutionErrorCode.REQUEST_TIMEOUT.code(), 3
        )).isFalse();
    }

    @Test
    void shouldNotRetryWriteWithoutIdempotencyKey() {
        assertThat(policy.shouldRetry(
                step("POST", Map.of()), ExecutionErrorCode.REMOTE_REQUEST_FAILED.code(), 1
        )).isFalse();
        assertThat(policy.shouldRetry(
                step("PATCH", Map.of()), ExecutionErrorCode.REQUEST_TIMEOUT.code(), 1
        )).isFalse();
    }

    @Test
    void shouldRetryWriteOnlyWhenIdempotencyKeyExists() {
        assertThat(policy.shouldRetry(
                step("POST", Map.of("Idempotency-Key", "order-001")),
                ExecutionErrorCode.REMOTE_REQUEST_FAILED.code(),
                1
        )).isTrue();
        assertThat(policy.shouldRetry(
                step("POST", Map.of("idempotency-key", "order-001")),
                ExecutionErrorCode.REQUEST_TIMEOUT.code(),
                2
        )).isTrue();
    }

    @Test
    void shouldNotRetryAssertionOrServerResponseFailure() {
        assertThat(policy.shouldRetry(
                step("GET", Map.of()), ExecutionErrorCode.INVALID_JSON_PATH.code(), 1
        )).isFalse();
        assertThat(policy.shouldRetry(
                step("GET", Map.of()), ExecutionErrorCode.RESPONSE_TOO_LARGE.code(), 1
        )).isFalse();
    }

    private ExecutionStepRequest step(String method, Map<String, String> headers) {
        return new ExecutionStepRequest(
                "重试测试", method, "/orders", Map.of(), Map.of(), headers,
                null, null, List.of(), List.of(), false
        );
    }
}
