package com.dochelper.compatibility;

import java.time.Duration;
import java.time.Instant;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;

/**
 * SSE 兼容性验证接口。
 */
@RestController
@RequestMapping("/api/compatibility")
public class CompatibilityStreamController {

    /**
     * 依次发送 Agent 生命周期模拟事件。
     *
     * @return SSE 事件流
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<CompatibilityEvent>> stream() {
        return Flux.just(
                        new CompatibilityEvent(1, "PLANNING", "正在生成执行计划", Instant.now()),
                        new CompatibilityEvent(2, "EXECUTING", "正在调用兼容性工具", Instant.now()),
                        new CompatibilityEvent(3, "SUCCEEDED", "流式验证完成", Instant.now())
                )
                .delayElements(Duration.ofMillis(10))
                .map(event -> ServerSentEvent.<CompatibilityEvent>builder()
                        .id(String.valueOf(event.sequence()))
                        .event("agent-event")
                        .data(event)
                        .build());
    }
}
