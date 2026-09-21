package com.dochelper.agent.application;

import java.util.function.Supplier;

import com.dochelper.agent.config.AgentProperties;
import com.dochelper.agent.domain.repository.AgentTaskRepository;
import com.dochelper.agent.exception.AgentErrorCode;
import com.dochelper.common.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * 在单实例内串行完成任务容量检查与持久化，避免并发创建绕过上限。
 */
@Component
public class AgentTaskAdmissionService {

    private final AgentTaskRepository repository;
    private final AgentProperties properties;

    public AgentTaskAdmissionService(AgentTaskRepository repository, AgentProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * 检查全局与项目容量，并在同一临界区创建任务。
     *
     * @param projectId 项目标识
     * @param taskFactory 已通过参数校验的任务创建逻辑
     * @return 新建任务
     * @param <T> 创建结果类型
     */
    public synchronized <T> T admit(Long projectId, Supplier<T> taskFactory) {
        long projectActive = repository.countActiveTasks(projectId);
        if (projectActive >= properties.maxConcurrentTasksPerProject()) {
            throw new BusinessException(
                    AgentErrorCode.TASK_CAPACITY_EXCEEDED,
                    "当前项目已有 " + projectActive + " 个非终态任务，上限为 "
                            + properties.maxConcurrentTasksPerProject() + "，请等待、取消或处理待确认任务"
            );
        }
        long globalActive = repository.countActiveTasks();
        if (globalActive >= properties.maxConcurrentTasks()) {
            throw new BusinessException(
                    AgentErrorCode.TASK_CAPACITY_EXCEEDED,
                    "当前共有 " + globalActive + " 个非终态任务，上限为 "
                            + properties.maxConcurrentTasks() + "，请稍后再试"
            );
        }
        return taskFactory.get();
    }
}
