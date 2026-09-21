package com.dochelper.agent.application;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.dochelper.agent.api.vo.AgentTaskEventResponse;
import com.dochelper.agent.api.vo.AgentTaskResponse;
import com.dochelper.agent.config.AgentProperties;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 将持久化 Agent 事件转换为支持游标续传的响应式事件流。
 */
@Service
public class AgentEventStreamService {

    private final AgentTaskService taskService;
    private final AgentProperties properties;

    public AgentEventStreamService(
            AgentTaskService taskService,
            AgentProperties properties
    ) {
        this.taskService = taskService;
        this.properties = properties;
    }

    /**
     * 从指定事件序号之后开始推送，终态任务在最后一批事件后关闭。
     */
    public Flux<AgentTaskEventResponse> stream(
            Long projectId,
            Long taskId,
            long afterSequence
    ) {
        AtomicLong cursor = new AtomicLong(Math.max(0, afterSequence));
        Duration interval = properties.eventPollInterval();
        return Flux.interval(Duration.ZERO, interval)
                .concatMap(ignored -> Mono.fromCallable(() ->
                                poll(projectId, taskId, cursor))
                        .subscribeOn(Schedulers.boundedElastic()))
                .takeUntil(PollBatch::terminal)
                .concatMapIterable(PollBatch::events);
    }

    private PollBatch poll(Long projectId, Long taskId, AtomicLong cursor) {
        List<AgentTaskEventResponse> events = taskService.events(
                projectId,
                taskId,
                cursor.get(),
                200
        );
        if (!events.isEmpty()) {
            cursor.set(events.getLast().sequenceNo());
        }
        AgentTaskResponse task = taskService.get(projectId, taskId);
        boolean terminal = "SUCCEEDED".equals(task.status())
                || "NEEDS_REVIEW".equals(task.status())
                || "FAILED".equals(task.status())
                || "CANCELLED".equals(task.status());
        return new PollBatch(events, terminal);
    }

    private record PollBatch(List<AgentTaskEventResponse> events, boolean terminal) {
    }
}
