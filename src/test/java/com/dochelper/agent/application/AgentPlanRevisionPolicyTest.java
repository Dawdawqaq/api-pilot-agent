package com.dochelper.agent.application;

import java.util.List;
import java.util.Map;

import com.dochelper.agent.domain.AgentPlanStep;
import com.dochelper.common.exception.BusinessException;
import com.dochelper.executor.api.dto.ExecutionStepRequest;
import com.dochelper.executor.api.dto.ResponseAssertionRequest;
import com.dochelper.executor.api.dto.VariableExtractorRequest;
import com.dochelper.executor.domain.AssertionType;
import com.fasterxml.jackson.databind.node.IntNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * 证明自动修复不能通过删断言、修改预期值或换接口取得假成功。
 */
class AgentPlanRevisionPolicyTest {
    private final AgentPlanRevisionPolicy policy = new AgentPlanRevisionPolicy(new com.fasterxml.jackson.databind.ObjectMapper());

    @Test
    void shouldAllowExtractionRepairOnUnfinishedStep() {
        assertThatCode(() -> policy.validate(List.of(step("/posts", "$.id", 200)),
                List.of(step("/posts", "$.data.id", 200)), 0)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectRemovedExtractor() {
        AgentPlanStep original = step("/posts", "$.missing", 200);
        var request = original.request();
        AgentPlanStep weakened = new AgentPlanStep(0, original.objective(), new ExecutionStepRequest(
                request.name(), request.method(), request.path(), request.pathVariables(), request.queryParams(),
                request.headers(), request.body(), request.authentication(), List.of(), request.assertions(), false));
        assertThatThrownBy(() -> policy.validate(List.of(original), List.of(weakened), 0))
                .isInstanceOf(BusinessException.class).hasMessageContaining("提取变量");
    }

    @Test
    void shouldRejectChangedExpectedValue() {
        assertThatThrownBy(() -> policy.validate(List.of(step("/posts", "$.id", 200)),
                List.of(step("/posts", "$.id", 500)), 0))
                .isInstanceOf(BusinessException.class).hasMessageContaining("断言");
    }

    @Test
    void shouldRejectRemovedAssertions() {
        AgentPlanStep original = step("/posts", "$.id", 200);
        var request = original.request();
        AgentPlanStep weakened = new AgentPlanStep(0, original.objective(), new ExecutionStepRequest(
                request.name(), request.method(), request.path(), request.pathVariables(), request.queryParams(),
                request.headers(), request.body(), request.authentication(), request.extractors(), List.of(), false));
        assertThatThrownBy(() -> policy.validate(List.of(original), List.of(weakened), 0))
                .isInstanceOf(BusinessException.class).hasMessageContaining("断言");
    }

    @Test
    void shouldRejectReplacementEndpoint() {
        assertThatThrownBy(() -> policy.validate(List.of(step("/posts", "$.id", 200)),
                List.of(step("/health", "$.id", 200)), 0))
                .isInstanceOf(BusinessException.class).hasMessageContaining("接口");
    }

    @Test
    void shouldRejectChangesToCompletedSteps() {
        assertThatThrownBy(() -> policy.validate(List.of(step("/posts", "$.id", 200)),
                List.of(step("/posts", "$.data.id", 200)), 1))
                .isInstanceOf(BusinessException.class).hasMessageContaining("已成功");
    }

    private AgentPlanStep step(String path, String extraction, int status) {
        return new AgentPlanStep(0, "查询帖子", new ExecutionStepRequest("查询", "GET", path,
                Map.of(), Map.of(), Map.of(), null, null,
                List.of(new VariableExtractorRequest("postId", extraction)),
                List.of(new ResponseAssertionRequest(AssertionType.STATUS_CODE, null, IntNode.valueOf(status), null)), false));
    }
}
