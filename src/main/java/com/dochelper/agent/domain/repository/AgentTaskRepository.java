package com.dochelper.agent.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.dochelper.agent.domain.AgentConfirmation;
import com.dochelper.agent.domain.AgentConversation;
import com.dochelper.agent.domain.AgentMessage;
import com.dochelper.agent.domain.AgentModelCall;
import com.dochelper.agent.domain.AgentTask;
import com.dochelper.agent.domain.AgentTaskEvent;
import com.dochelper.agent.domain.AgentTaskStatus;
import com.dochelper.agent.domain.AgentToolCall;
import com.dochelper.agent.domain.ConfirmationStatus;

/**
 * Agent 任务、事件、工具调用和会话统一仓储。
 */
public interface AgentTaskRepository {

    AgentConversation createConversation(AgentConversation conversation);

    Optional<AgentConversation> findConversation(Long projectId, Long conversationId);

    AgentTask createTask(AgentTask task);

    void appendMessage(AgentMessage message);

    Optional<AgentTask> findTask(Long projectId, Long taskId);

    List<AgentTask> findTasks(Long projectId, int limit);

    void updatePlan(Long taskId, String planJson, String contextJsonRedacted);

    void updateProgress(
            Long taskId,
            AgentTaskStatus status,
            int currentStep,
            int toolCallCount,
            int replanCount,
            String resultSummary,
            String errorCode,
            String errorMessage,
            LocalDateTime startedAt,
            LocalDateTime completedAt
    );

    void requestCancel(Long taskId);

    AgentTaskEvent appendEvent(AgentTaskEvent event);

    List<AgentTaskEvent> findEvents(Long taskId, long afterSequence, int limit);

    void createToolCall(AgentToolCall toolCall);

    void completeToolCall(AgentToolCall toolCall);

    List<AgentToolCall> findToolCalls(Long taskId);

    void createModelCall(AgentModelCall modelCall);

    List<AgentModelCall> findModelCalls(Long taskId);

    AgentConfirmation createConfirmation(AgentConfirmation confirmation);

    Optional<AgentConfirmation> findConfirmation(Long taskId, int stepIndex);

    boolean decideConfirmation(
            Long confirmationId,
            ConfirmationStatus expectedStatus,
            ConfirmationStatus decidedStatus,
            String decisionNote,
            LocalDateTime decidedAt
    );
}
