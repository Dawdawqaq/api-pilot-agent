package com.dochelper.agent.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.dochelper.agent.domain.AgentPlanStep;
import com.dochelper.executor.api.dto.ExecutionStepRequest;

import org.springframework.stereotype.Component;

/**
 * 保存任务运行期间不能明文落库的变量和计划。
 *
 * <p>生产环境应替换为凭据引用或外部 Secret Vault。MVP 选择进程内保存，
 * 保证密码和 Token 不进入数据库；应用重启后需要重新提交含凭据的任务。</p>
 */
@Component
public class AgentRuntimeRegistry {

    private final Map<Long, RuntimeContext> contexts = new ConcurrentHashMap<>();

    public void create(
            Long taskId,
            String goal,
            Map<String, Object> initialVariables,
            List<ExecutionStepRequest> planHint
    ) {
        contexts.put(taskId, new RuntimeContext(
                goal,
                initialVariables == null
                        ? Map.of()
                        : java.util.Collections.unmodifiableMap(
                                new java.util.LinkedHashMap<>(initialVariables)
                        ),
                planHint == null ? List.of() : List.copyOf(planHint),
                List.of()
        ));
    }

    public Optional<RuntimeContext> find(Long taskId) {
        return Optional.ofNullable(contexts.get(taskId));
    }

    public void updatePlan(Long taskId, List<AgentPlanStep> plan) {
        contexts.computeIfPresent(taskId, (ignored, current) ->
                new RuntimeContext(
                        current.goal(),
                        current.initialVariables(),
                        current.planHint(),
                        List.copyOf(plan)
                )
        );
    }

    public void remove(Long taskId) {
        contexts.remove(taskId);
    }

    /**
     * Agent 未脱敏运行上下文。
     */
    public record RuntimeContext(
            String goal,
            Map<String, Object> initialVariables,
            List<ExecutionStepRequest> planHint,
            List<AgentPlanStep> plan
    ) {
    }
}
