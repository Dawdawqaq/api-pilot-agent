package com.dochelper.executor.application;

import java.net.URI;
import java.util.Map;

import com.dochelper.common.exception.BusinessException;
import com.dochelper.executor.api.dto.ExecutionStepRequest;
import com.dochelper.project.domain.ProjectEnvironment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证方法白名单、危险操作确认和 SSRF 私网拦截。
 */
class TargetAccessPolicyTest {

    private final TargetAccessPolicy policy = new TargetAccessPolicy();

    @Test
    void shouldRejectMethodOutsideEnvironmentPolicy() {
        assertThatThrownBy(() -> policy.validateAndNormalizeMethod(
                environment(false, "GET,POST"),
                step("PATCH", true)
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许 PATCH");
    }

    @Test
    void shouldLeaveWriteConfirmationToServerPolicy() {
        assertThat(policy.validateAndNormalizeMethod(
                environment(true, "GET,DELETE"),
                step("DELETE", false)
        )).isEqualTo("DELETE");
    }

    @Test
    void shouldRejectLoopbackWhenEnvironmentDoesNotAllowPrivateNetwork() {
        assertThatThrownBy(() -> policy.validateTarget(
                environment(false, "GET"),
                URI.create("http://localhost:8080/users")
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("私网");
    }

    private ProjectEnvironment environment(boolean allowPrivateNetwork, String methods) {
        return new ProjectEnvironment(
                1L,
                1L,
                "test",
                "http://localhost:8080",
                methods,
                allowPrivateNetwork,
                true,
                null,
                null
        );
    }

    private ExecutionStepRequest step(String method, boolean confirmed) {
        return new ExecutionStepRequest(
                "测试步骤",
                method,
                "/users",
                Map.of(),
                Map.of(),
                Map.of(),
                null,
                null,
                null,
                null,
                confirmed
        );
    }
}
