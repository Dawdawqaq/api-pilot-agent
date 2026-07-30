package com.dochelper.common.reactive;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 将 JDBC、MinIO 等阻塞调用隔离到 Reactor 弹性线程池。
 */
@Component
public class BlockingOperationExecutor {

    /**
     * 在阻塞任务专用线程池执行同步操作。
     *
     * @param supplier 同步操作
     * @param <T> 返回值类型
     * @return 延迟执行的异步结果
     */
    public <T> Mono<T> execute(Supplier<T> supplier) {
        return Mono.fromCallable(supplier::get)
                .subscribeOn(Schedulers.boundedElastic());
    }
}
