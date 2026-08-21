package com.dochelper.executor.application;

import java.util.List;
import java.util.Map;

import com.dochelper.common.exception.BusinessException;
import com.dochelper.executor.api.dto.ExecutionStepRequest;
import com.dochelper.executor.domain.OperationRisk;
import com.dochelper.openapi.domain.ApiEndpoint;
import com.dochelper.openapi.domain.repository.OpenApiCatalogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 OpenAPI 运行时白名单、模板路径匹配和写操作确认。
 */
class EndpointExecutionPolicyTest {

    private EndpointExecutionPolicy policy;
    private OpenApiCatalogRepository repository;

    @BeforeEach
    void setUp() {
        repository = mock(OpenApiCatalogRepository.class);
        policy = new EndpointExecutionPolicy(repository);
        when(repository.findEndpoints(1L, null)).thenReturn(List.of(
                endpoint(10L, "/users/{id}", "GET", "getUser"),
                endpoint(11L, "/users", "POST", "createUser"),
                endpoint(12L, "/auth/login", "POST", "login")
        ));
    }

    @Test
    void shouldMatchConcretePathAgainstTemplate() {
        var operation = policy.validate(1L, step("GET", "/users/42"), false);

        assertThat(operation.endpointId()).isEqualTo(10L);
        assertThat(operation.risk()).isEqualTo(OperationRisk.READ_ONLY);
    }

    @Test
    void shouldRejectOutOfCatalogAndMethodMismatch() {
        assertThatThrownBy(() -> policy.validate(1L, step("GET", "/admin/drop"), false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不在当前 OpenAPI 目录");
        assertThatThrownBy(() -> policy.validate(1L, step("DELETE", "/users/42"), true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持 DELETE");
    }

    @Test
    void shouldRequireServerConfirmationForMutatingOperation() {
        assertThatThrownBy(() -> policy.validate(1L, step("POST", "/users"), false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Owner 确认");
        assertThat(policy.validate(1L, step("POST", "/users"), true).risk())
                .isEqualTo(OperationRisk.MUTATING);
        assertThat(policy.validate(1L, step("POST", "/auth/login"), false).risk())
                .isEqualTo(OperationRisk.SAFE_AUTH);
    }

    @Test
    void shouldRejectEncodedTraversal() {
        assertThatThrownBy(() -> policy.validate(1L, step("GET", "/%2e%2e/admin"), false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("安全标准化");
    }

    private ExecutionStepRequest step(String method, String path) {
        return new ExecutionStepRequest(
                "测试", method, path, Map.of(), Map.of(), Map.of(), null,
                null, List.of(), List.of(), false
        );
    }

    private ApiEndpoint endpoint(Long id, String path, String method, String operationId) {
        return new ApiEndpoint(
                id, 1L, 2L, path, method, operationId, operationId, null,
                "[]", false, null, "{}", null, List.of()
        );
    }
}
