package com.dochelper.agent.application;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.dochelper.agent.domain.AgentPlanStep;
import com.dochelper.executor.api.dto.ExecutionStepRequest;
import com.dochelper.executor.api.dto.VariableExtractorRequest;
import com.dochelper.executor.domain.ExecutionStepResult;
import com.dochelper.openapi.domain.ApiEndpoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Stub 重规划会保留成功步骤并只修订失败步骤。
 */
class DeterministicAgentReplanTest {

    @Test
    void shouldLockCompletedStepsAndReviseFailedExtractor() {
        AgentPlanStep completed = new AgentPlanStep(0, "健康检查", step("/health", List.of()));
        AgentPlanStep failed = new AgentPlanStep(
                1,
                "提取用户标识",
                step("/users/me", List.of(new VariableExtractorRequest("userId", "$.data.id")))
        );
        ExecutionStepResult observed = new ExecutionStepResult(
                0, "健康检查", "GET", "/health", 200, null,
                Map.of(), List.of(), true, 1, null
        );

        List<AgentPlanStep> revised = new DeterministicAgentPlanner().replan(new AgentReplanContext(
                1L, 1L, "查询用户", List.of(completed, failed), List.of(observed),
                Set.of(), "EXECUTOR_422_002", "JSONPath 未命中", List.of()
        ));

        assertThat(revised.get(0)).isEqualTo(completed);
        assertThat(revised.get(1).request().extractors()).isEmpty();
        assertThat(revised.get(1).objective()).contains("修订");
    }

    @Test
    void shouldReplaceVariableDependentFailureWithSafeCatalogCandidate() {
        AgentPlanStep completed = new AgentPlanStep(0, "健康检查", step("/health", List.of()));
        AgentPlanStep failed = new AgentPlanStep(1, "读取订单", new ExecutionStepRequest(
                "读取订单", "GET", "/orders/{orderId}", Map.of("orderId", "{{orderId}}"),
                Map.of(), Map.of(), null, null, List.of(), List.of(), false
        ));
        ApiEndpoint fallback = new ApiEndpoint(
                9L, 1L, 1L, "/orders", "GET", "listOrders", "查询订单列表", null,
                "[]", false, null, "{}", null, List.of()
        );

        List<AgentPlanStep> revised = new DeterministicAgentPlanner().replan(new AgentReplanContext(
                1L, 1L, "查询订单", List.of(completed, failed), List.of(new ExecutionStepResult(
                        0, "健康检查", "GET", "/health", 200, null,
                        Map.of(), List.of(), true, 1, null
                )), Set.of(), "EXECUTOR_422_001", "缺少 orderId", List.of(fallback)
        ));

        assertThat(revised.get(0)).isEqualTo(completed);
        assertThat(revised.get(1).request().path()).isEqualTo("/orders");
        assertThat(revised.get(1).request().pathVariables()).isEmpty();
    }

    private ExecutionStepRequest step(
            String path,
            List<VariableExtractorRequest> extractors
    ) {
        return new ExecutionStepRequest(
                "步骤", "GET", path, Map.of(), Map.of(), Map.of(), null,
                null, extractors, List.of(), false
        );
    }
}
