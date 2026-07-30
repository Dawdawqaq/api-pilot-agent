package com.dochelper.agent.application;

import java.util.List;

import com.dochelper.agent.domain.AgentPlanStep;

/**
 * Agent 计划生成端口，允许 Stub 与真实模型实现互换。
 */
public interface AgentPlanner {

    /**
     * 将自然语言目标转换为强类型执行计划。
     *
     * @param context 规划上下文
     * @return 执行计划
     */
    List<AgentPlanStep> plan(AgentPlanningContext context);
}
