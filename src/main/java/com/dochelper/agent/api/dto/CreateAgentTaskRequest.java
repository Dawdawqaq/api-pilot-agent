package com.dochelper.agent.api.dto;

import java.util.List;
import java.util.Map;

import com.dochelper.executor.api.dto.ExecutionStepRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建 Agent 任务请求。
 *
 * @param environmentId 执行环境标识
 * @param conversationId 可复用的会话标识
 * @param goal 自然语言任务目标
 * @param initialVariables 初始变量
 * @param planHint Stub 验收或人工修正使用的强类型计划提示
 */
public record CreateAgentTaskRequest(
        Long environmentId,
        Long conversationId,
        @NotBlank @Size(max = 2000) String goal,
        Map<String, Object> initialVariables,
        @Size(max = 20) @Valid List<ExecutionStepRequest> planHint
) {
}
