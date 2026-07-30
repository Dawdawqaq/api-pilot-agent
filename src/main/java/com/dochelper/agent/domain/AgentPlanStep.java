package com.dochelper.agent.domain;

import com.dochelper.executor.api.dto.ExecutionStepRequest;

/**
 * Agent 生成的强类型执行计划步骤。
 *
 * @param index 步骤序号
 * @param objective 步骤目标
 * @param request 受控 HTTP 请求
 */
public record AgentPlanStep(
        int index,
        String objective,
        ExecutionStepRequest request
) {

    /**
     * 判断步骤是否需要人工确认。
     *
     * @return 是否为危险操作
     */
    public boolean dangerous() {
        return "DELETE".equalsIgnoreCase(request.method());
    }
}
