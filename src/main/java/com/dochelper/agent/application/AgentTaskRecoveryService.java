package com.dochelper.agent.application;

import java.time.LocalDateTime;

import com.dochelper.agent.domain.AgentTask;
import com.dochelper.agent.domain.AgentTaskStatus;
import com.dochelper.agent.domain.repository.AgentTaskRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 启动及周期回收过期租约；恢复动作领取任务后再读取敏感上下文。
 */
@Component
public class AgentTaskRecoveryService {

    private final AgentTaskRepository repository;
    private final AgentTaskRunner runner;
    private final TaskExecutor taskExecutor;
    private final java.util.Set<Long> dispatchedTasks = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public AgentTaskRecoveryService(
            AgentTaskRepository repository,
            AgentTaskRunner runner,
            @Qualifier("agentTaskExecutor") TaskExecutor taskExecutor
    ) {
        this.repository = repository;
        this.runner = runner;
        this.taskExecutor = taskExecutor;
    }

    @EventListener(ApplicationReadyEvent.class)
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "${dochelper.agent.recovery-interval-ms:5000}")
    public void recover() {
        for (AgentTask task : repository.findRecoverableTasks(LocalDateTime.now(), 50)) {
            // 排队阶段尚未领取数据库租约，先在本实例去重，防止周期扫描堆积重复任务。
            if (!dispatchedTasks.add(task.id())) {
                continue;
            }
            try {
                taskExecutor.execute(() -> {
                    try {
                        if (task.status() == AgentTaskStatus.RECEIVED) {
                            runner.runNew(task.projectId(), task.id());
                        } else {
                            runner.resumeRecovered(task.projectId(), task.id());
                        }
                    } finally {
                        dispatchedTasks.remove(task.id());
                    }
                });
            } catch (RuntimeException exception) {
                dispatchedTasks.remove(task.id());
                throw exception;
            }
        }
    }

}
