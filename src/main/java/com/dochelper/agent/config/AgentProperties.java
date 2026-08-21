package com.dochelper.agent.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 执行限制配置。
 *
 * @param maxSteps 最大计划步骤数
 * @param maxToolCalls 最大工具调用次数
 * @param maxReplans 最大重新规划次数
 * @param maxPlanModifications 最大人工计划修改轮次
 * @param maxToolRetries 单次工具最大重试次数
 * @param taskTimeout 任务总超时
 * @param confirmationTimeout 人工确认有效期
 * @param eventPollInterval 事件流轮询间隔
 */
@ConfigurationProperties(prefix = "dochelper.agent")
public record AgentProperties(
        int maxSteps,
        int maxToolCalls,
        int maxReplans,
        int maxPlanModifications,
        int maxToolRetries,
        Duration taskTimeout,
        Duration confirmationTimeout,
        Duration eventPollInterval,
        Duration leaseDuration,
        int maxTotalModelTokens,
        int defaultEndpointTopK
) {
}
