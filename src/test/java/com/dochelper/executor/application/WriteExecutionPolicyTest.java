package com.dochelper.executor.application;

import java.util.List;
import java.util.Map;

import com.dochelper.common.exception.BusinessException;
import com.dochelper.executor.api.dto.ExecutionStepRequest;
import com.dochelper.executor.domain.AgentStepExecution;
import com.dochelper.executor.domain.StepExecutionStatus;
import com.dochelper.executor.exception.ExecutionErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * 验证写请求超时、执行中断和上下文缺失均不能触发无依据的重放。
 */
class WriteExecutionPolicyTest {
    private final WriteExecutionPolicy policy = new WriteExecutionPolicy();

    @Test
    void shouldRequireReviewForWriteTimeoutAndPostResponseExtractionFailure() {
        for (var error : List.of(ExecutionErrorCode.REQUEST_TIMEOUT, ExecutionErrorCode.REMOTE_REQUEST_FAILED)) {
            assertThat(policy.classifyFailure(step("POST"), new BusinessException(error), false).getErrorCode())
                    .isEqualTo(ExecutionErrorCode.WRITE_RESULT_REQUIRES_REVIEW);
        }
        assertThat(policy.classifyFailure(step("POST"), new BusinessException(ExecutionErrorCode.INVALID_JSON_PATH), true)
                .getErrorCode()).isEqualTo(ExecutionErrorCode.WRITE_RESULT_REQUIRES_REVIEW);
    }

    @Test
    void shouldPreserveRepairablePreSendErrorAndReadFailure() {
        var missing = new BusinessException(ExecutionErrorCode.VARIABLE_NOT_FOUND);
        assertThat(policy.classifyFailure(step("POST"), missing, false)).isSameAs(missing);
        var timeout = new BusinessException(ExecutionErrorCode.REQUEST_TIMEOUT);
        assertThat(policy.classifyFailure(step("GET"), timeout, false)).isSameAs(timeout);
    }

    @Test
    void shouldBlockWriteWhenPriorAttemptCannotBeRestored() {
        for (var status : StepExecutionStatus.values()) {
            var attempt = new AgentStepExecution(1L, 2L, 3L, 0, 1, status, "fingerprint", null,
                    "[]", null, null, null, null, null, 0L, null, null);
            assertThatThrownBy(() -> policy.requireFreshWrite(step("POST"), 0, List.of(attempt)))
                    .isInstanceOf(BusinessException.class).extracting("errorCode")
                    .isEqualTo(ExecutionErrorCode.WRITE_RESULT_REQUIRES_REVIEW);
            assertThatCode(() -> policy.requireFreshWrite(step("GET"), 0, List.of(attempt))).doesNotThrowAnyException();
        }
    }

    private ExecutionStepRequest step(String method) {
        return new ExecutionStepRequest("步骤", method, "/posts", Map.of(), Map.of(), Map.of(),
                null, null, List.of(), List.of(), false);
    }
}
