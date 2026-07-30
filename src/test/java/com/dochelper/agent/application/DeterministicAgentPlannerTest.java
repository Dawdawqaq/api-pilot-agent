package com.dochelper.agent.application;

import java.util.List;

import com.dochelper.agent.domain.AgentPlanStep;
import com.dochelper.executor.api.dto.ExecutionStepRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Stub 规划器能够稳定保留强类型多步骤计划。
 */
class DeterministicAgentPlannerTest {

    @Test
    void shouldConvertPlanHintsToIndexedPlan() {
        ExecutionStepRequest first = new ExecutionStepRequest(
                "登录",
                "POST",
                "/login",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false
        );
        ExecutionStepRequest second = new ExecutionStepRequest(
                "查询",
                "GET",
                "/users",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false
        );

        List<AgentPlanStep> plan = new DeterministicAgentPlanner().plan(
                new AgentPlanningContext(
                        2L,
                        1L,
                        "登录后查询用户",
                        List.of(),
                        List.of(),
                        java.util.Set.of(),
                        List.of(first, second)
                )
        );

        assertThat(plan).extracting(AgentPlanStep::index).containsExactly(0, 1);
        assertThat(plan).extracting(AgentPlanStep::objective)
                .containsExactly("登录", "查询");
    }
}
