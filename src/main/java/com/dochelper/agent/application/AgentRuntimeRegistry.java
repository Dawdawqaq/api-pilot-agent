package com.dochelper.agent.application;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dochelper.agent.domain.AgentPlanStep;
import com.dochelper.executor.api.dto.ExecutionStepRequest;
import com.dochelper.secret.application.SecretStore;
import org.springframework.stereotype.Component;

/**
 * 通过 SecretStore 保存任务运行上下文，数据库任务表只保留不透明引用。
 */
@Component
public class AgentRuntimeRegistry {

    private static final Duration CONTEXT_TTL = Duration.ofHours(4);

    private final Map<Long, RuntimeEntry> contexts = new ConcurrentHashMap<>();
    private final SecretStore secretStore;
    private final ObjectMapper objectMapper;

    public AgentRuntimeRegistry(SecretStore secretStore, ObjectMapper objectMapper) {
        this.secretStore = secretStore;
        this.objectMapper = objectMapper;
    }

    public String create(
            Long taskId,
            String goal,
            Map<String, Object> initialVariables,
            List<ExecutionStepRequest> planHint
    ) {
        RuntimeContext context = new RuntimeContext(
                goal,
                initialVariables == null ? Map.of() : Map.copyOf(initialVariables),
                planHint == null ? List.of() : List.copyOf(planHint),
                List.of()
        );
        String reference = save(taskId, context);
        contexts.put(taskId, new RuntimeEntry(reference, context));
        return reference;
    }

    public Optional<RuntimeContext> find(Long taskId) {
        RuntimeEntry entry = contexts.get(taskId);
        return entry == null ? Optional.empty() : Optional.of(entry.context());
    }

    /**
     * 在领取任务后按持久化引用恢复，包括应用重启前尚未确认的计划。
     */
    public RuntimeContext require(com.dochelper.agent.domain.AgentTask task) {
        var existing = find(task.id());
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            String reference = objectMapper.readTree(task.contextJsonRedacted()).path("runtimeContextRef").asText("");
            if (!reference.isBlank() && restore(task.id(), reference)) {
                return find(task.id()).orElseThrow();
            }
        } catch (Exception ignored) {
            // 不把密文、解密异常或原始上下文写入错误信息。
        }
        throw new com.dochelper.common.exception.BusinessException(
                com.dochelper.agent.exception.AgentErrorCode.RECOVERY_UNAVAILABLE);
    }

    public Optional<String> reference(Long taskId) {
        RuntimeEntry entry = contexts.get(taskId);
        return entry == null ? Optional.empty() : Optional.of(entry.reference());
    }

    public String updatePlan(Long taskId, List<AgentPlanStep> plan) {
        PreparedPlan prepared = preparePlan(taskId, plan);
        activatePlan(taskId, prepared);
        return prepared.reference();
    }

    /**
     * 先保存候选上下文，数据库提交失败时仍保留原计划及其恢复引用。
     */
    public PreparedPlan preparePlan(Long taskId, List<AgentPlanStep> plan) {
        RuntimeEntry current = contexts.get(taskId);
        if (current == null) {
            throw new IllegalStateException("Agent 运行上下文不存在");
        }
        RuntimeContext revised = new RuntimeContext(
                current.context().goal(), current.context().initialVariables(),
                current.context().planHint(), List.copyOf(plan)
        );
        String reference = save(taskId, revised);
        return new PreparedPlan(reference, revised);
    }

    public void activatePlan(Long taskId, PreparedPlan prepared) {
        contexts.put(taskId, new RuntimeEntry(prepared.reference(), prepared.context()));
        // 旧引用由有效期回收，避免数据库提交前的异常造成恢复信息丢失。
    }

    public void discardPlan(PreparedPlan prepared) {
        secretStore.delete(prepared.reference());
    }

    public record PreparedPlan(String reference, RuntimeContext context) { }

    /**
     * 应用重启后使用任务表中的不透明引用恢复上下文。
     */
    public boolean restore(Long taskId, String reference) {
        Optional<String> value = secretStore.get(reference);
        if (value.isEmpty()) {
            return false;
        }
        try {
            RuntimeContext context = objectMapper.readValue(value.get(), RuntimeContext.class);
            contexts.put(taskId, new RuntimeEntry(reference, context));
            return true;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Agent 运行上下文数据损坏", exception);
        }
    }

    public void remove(Long taskId) {
        RuntimeEntry entry = contexts.remove(taskId);
        if (entry != null) {
            secretStore.delete(entry.reference());
        }
    }

    private String save(Long taskId, RuntimeContext context) {
        try {
            return secretStore.put(
                    "agent-runtime:" + taskId,
                    objectMapper.writeValueAsString(context),
                    CONTEXT_TTL
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Agent 运行上下文序列化失败", exception);
        }
    }

    private record RuntimeEntry(String reference, RuntimeContext context) {
    }

    /**
     * Agent 未脱敏运行上下文，只能存放在 SecretStore。
     */
    public record RuntimeContext(
            String goal,
            Map<String, Object> initialVariables,
            List<ExecutionStepRequest> planHint,
            List<AgentPlanStep> plan
    ) {
    }
}
