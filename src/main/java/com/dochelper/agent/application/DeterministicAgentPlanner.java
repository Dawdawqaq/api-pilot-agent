package com.dochelper.agent.application;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.node.IntNode;
import com.dochelper.agent.domain.AgentPlanStep;
import com.dochelper.agent.exception.AgentErrorCode;
import com.dochelper.common.exception.BusinessException;
import com.dochelper.executor.api.dto.ExecutionStepRequest;
import com.dochelper.executor.api.dto.ResponseAssertionRequest;
import com.dochelper.executor.domain.AssertionType;
import com.dochelper.openapi.domain.ApiEndpoint;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 无 API Key 阶段使用的确定性规划器。
 *
 * <p>显式计划提示用于稳定复现多接口链路；未提供提示时，会从 OpenAPI
 * 中选择与目标最相关且无需路径变量的接口，保证本地开发仍可验证完整状态流转。</p>
 */
@Component
@Profile("stub")
public class DeterministicAgentPlanner implements AgentPlanner {

    @Override
    public List<AgentPlanStep> plan(AgentPlanningContext context) {
        if (context.planHint() != null && !context.planHint().isEmpty()) {
            return mapHints(context.planHint());
        }
        String normalizedGoal = context.goal().toLowerCase(Locale.ROOT);
        return context.endpoints().stream()
                .filter(endpoint -> !endpoint.path().contains("{"))
                .max(Comparator.comparingInt(endpoint -> score(endpoint, normalizedGoal)))
                .map(endpoint -> List.of(singleEndpointPlan(endpoint)))
                .orElseThrow(() -> new BusinessException(
                        AgentErrorCode.PLANNING_FAILED,
                        "Stub 规划器没有找到无需路径变量的接口，请导入 OpenAPI 或提供 planHint"
                ));
    }

    @Override
    public List<AgentPlanStep> replan(AgentReplanContext context) {
        int failedIndex = Math.min(context.completedSteps().size(), context.originalPlan().size() - 1);
        List<AgentPlanStep> revised = new java.util.ArrayList<>(context.originalPlan());
        AgentPlanStep failed = revised.get(failedIndex);
        ExecutionStepRequest request = failed.request();
        if ("EXECUTOR_422_002".equals(context.failureCode())) {
            request = new ExecutionStepRequest(
                    request.name(), request.method(), request.path(), request.pathVariables(),
                    request.queryParams(), request.headers(), request.body(), request.authentication(),
                    List.of(), request.assertions(), request.dangerousOperationConfirmed()
            );
        } else if ("EXECUTOR_422_001".equals(context.failureCode())
                || "EXECUTOR_400_002".equals(context.failureCode())) {
            request = context.remainingEndpoints().stream()
                    .filter(endpoint -> !endpoint.path().contains("{"))
                    .filter(endpoint -> "GET".equalsIgnoreCase(endpoint.httpMethod())
                            || "HEAD".equalsIgnoreCase(endpoint.httpMethod()))
                    .findFirst()
                    .map(endpoint -> singleEndpointPlan(endpoint).request())
                    .orElse(request);
        }
        revised.set(failedIndex, new AgentPlanStep(
                failed.index(), failed.objective() + "（根据失败观察修订）", request
        ));
        return List.copyOf(revised);
    }

    @Override
    public List<AgentPlanStep> modify(AgentModifyPlanContext context) {
        if (context.currentPlan().isEmpty()) {
            throw new BusinessException(AgentErrorCode.PLANNING_FAILED, "当前没有待修改的计划");
        }
        List<AgentPlanStep> revised = new java.util.ArrayList<>(context.currentPlan());
        AgentPlanStep first = revised.get(0);
        revised.set(0, new AgentPlanStep(
                first.index(),
                first.objective() + " [已按指令修改: " + context.modificationInstruction() + "]",
                first.request()
        ));
        return List.copyOf(revised);
    }

    private List<AgentPlanStep> mapHints(List<ExecutionStepRequest> hints) {
        return java.util.stream.IntStream.range(0, hints.size())
                .mapToObj(index -> new AgentPlanStep(
                        index,
                        hints.get(index).name(),
                        hints.get(index)
                ))
                .toList();
    }

    private AgentPlanStep singleEndpointPlan(ApiEndpoint endpoint) {
        ExecutionStepRequest request = new ExecutionStepRequest(
                endpoint.summary() == null || endpoint.summary().isBlank()
                        ? endpoint.httpMethod() + " " + endpoint.path()
                        : endpoint.summary(),
                endpoint.httpMethod(),
                endpoint.path(),
                Map.of(),
                Map.of(),
                Map.of(),
                null,
                null,
                List.of(),
                List.of(new ResponseAssertionRequest(
                        AssertionType.STATUS_CODE,
                        null,
                        IntNode.valueOf(200),
                        null
                )),
                false
        );
        return new AgentPlanStep(0, "验证接口是否返回成功状态", request);
    }

    private int score(ApiEndpoint endpoint, String goal) {
        int score = 0;
        score += contains(goal, endpoint.path()) ? 10 : 0;
        score += contains(goal, endpoint.operationId()) ? 6 : 0;
        score += contains(goal, endpoint.summary()) ? 4 : 0;
        score += "GET".equalsIgnoreCase(endpoint.httpMethod()) ? 1 : 0;
        return score;
    }

    private boolean contains(String goal, String candidate) {
        return candidate != null
                && !candidate.isBlank()
                && goal.contains(candidate.toLowerCase(Locale.ROOT));
    }
}
