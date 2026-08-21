package com.dochelper.agent.application;

import java.time.LocalDateTime;

import com.dochelper.agent.domain.AgentTask;
import com.dochelper.agent.domain.AgentTaskStatus;
import com.dochelper.agent.domain.repository.AgentTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 启动时回收过期租约并恢复可安全继续的 Agent 任务。
 */
@Component
public class AgentTaskRecoveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentTaskRecoveryService.class);

    private final AgentTaskRepository repository;
    private final AgentRuntimeRegistry runtimeRegistry;
    private final AgentTaskRunner runner;
    private final TaskExecutor taskExecutor;
    private final ObjectMapper objectMapper;

    public AgentTaskRecoveryService(
            AgentTaskRepository repository,
            AgentRuntimeRegistry runtimeRegistry,
            AgentTaskRunner runner,
            @Qualifier("agentTaskExecutor") TaskExecutor taskExecutor,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.runtimeRegistry = runtimeRegistry;
        this.runner = runner;
        this.taskExecutor = taskExecutor;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        for (AgentTask task : repository.findRecoverableTasks(LocalDateTime.now(), 50)) {
            if (task.status() == AgentTaskStatus.WAITING_CONFIRMATION) {
                continue;
            }
            String reference = runtimeReference(task);
            if (reference == null || !runtimeRegistry.restore(task.id(), reference)) {
                LOGGER.warn("任务 {} 缺少可恢复的运行时上下文，保持原状态等待人工处理", task.id());
                continue;
            }
            if (task.status() == AgentTaskStatus.RECEIVED) {
                taskExecutor.execute(() -> runner.runNew(task.projectId(), task.id()));
            } else if (!task.plan().isEmpty()) {
                taskExecutor.execute(() -> runner.resumeRecovered(task.projectId(), task.id()));
            }
        }
    }

    private String runtimeReference(AgentTask task) {
        try {
            String value = objectMapper.readTree(task.contextJsonRedacted())
                    .path("runtimeContextRef").asText("");
            return value.isBlank() ? null : value;
        } catch (Exception exception) {
            LOGGER.warn("任务 {} 的上下文摘要无法解析", task.id());
            return null;
        }
    }
}
