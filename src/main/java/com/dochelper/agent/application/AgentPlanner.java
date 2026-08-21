package com.dochelper.agent.application;

import java.util.List;

import com.dochelper.agent.domain.AgentPlanStep;
import com.dochelper.agent.exception.AgentErrorCode;
import com.dochelper.common.exception.BusinessException;

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

    /**
     * 根据已完成步骤和失败观察重新生成完整计划。
     */
    default List<AgentPlanStep> replan(AgentReplanContext context) {
        throw new BusinessException(AgentErrorCode.PLANNING_FAILED, "当前规划器不支持重规划");
    }

    /**
     * 根据人工指令对现有计划进行增量修改并返回新计划。
     *
     * @param context 计划修改上下文
     * @return 修改后的全新执行计划
     */
    default List<AgentPlanStep> modify(AgentModifyPlanContext context) {
        throw new BusinessException(AgentErrorCode.PLANNING_FAILED, "当前规划器不支持人工计划修改");
    }
}
