package com.dochelper.agent.application;

import java.util.List;
import java.util.Set;

import com.dochelper.agent.domain.AgentPlanStep;
import com.dochelper.openapi.domain.ApiEndpoint;

/**
 * 人工多轮计划修改上下文。
 *
 * @param taskId 任务标识
 * @param projectId 项目标识
 * @param goal 原始任务目标
 * @param currentPlan 当前待修改的计划步骤列表
 * @param modificationInstruction 人工自然语言修改指令
 * @param endpoints 可用 API 接口定义列表
 * @param initialVariableNames 初始可用变量名称集合
 */
public record AgentModifyPlanContext(
        Long taskId,
        Long projectId,
        String goal,
        List<AgentPlanStep> currentPlan,
        String modificationInstruction,
        List<ApiEndpoint> endpoints,
        Set<String> initialVariableNames
) {
}
