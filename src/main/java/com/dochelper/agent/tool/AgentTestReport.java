package com.dochelper.agent.tool;

import java.util.List;

/**
 * Agent 生成的结构化测试报告。
 *
 * @param taskId 任务标识
 * @param status 任务状态
 * @param summary 报告摘要
 * @param totalCalls 工具调用总数
 * @param successfulCalls 成功调用数
 * @param failedCalls 失败调用数
 * @param evidenceCitations 检索证据引用
 */
public record AgentTestReport(
        Long reportId,
        Long taskId,
        String status,
        String summary,
        int totalCalls,
        int successfulCalls,
        int failedCalls,
        int totalSteps,
        int passedSteps,
        int failedSteps,
        List<String> evidenceCitations
) {
}
