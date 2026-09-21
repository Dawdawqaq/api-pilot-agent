package com.dochelper.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Agent 异步任务线程池配置。
 */
@Configuration(proxyBeanMethods = false)
public class AgentExecutionConfiguration {

    /**
     * 创建有界 Agent 执行线程池，避免任务挤占 WebFlux 事件线程。
     *
     * @return Agent 任务执行器
     */
    @Bean("agentTaskExecutor")
    TaskExecutor agentTaskExecutor(AgentProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.min(2, properties.maxConcurrentTasks()));
        executor.setMaxPoolSize(properties.maxConcurrentTasks());
        executor.setQueueCapacity(properties.taskQueueCapacity());
        executor.setThreadNamePrefix("agent-task-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
