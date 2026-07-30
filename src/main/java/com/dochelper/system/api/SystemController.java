package com.dochelper.system.api;

import com.dochelper.common.api.ApiResponse;
import com.dochelper.common.reactive.BlockingOperationExecutor;
import com.dochelper.system.api.vo.InfrastructureOverviewResponse;
import com.dochelper.system.application.SystemOverviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

/**
 * 系统信息接口。
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final SystemOverviewService systemOverviewService;
    private final BlockingOperationExecutor blockingOperationExecutor;

    /**
     * 创建系统信息接口。
     *
     * @param systemOverviewService 系统概览服务
     * @param blockingOperationExecutor 阻塞操作执行器
     */
    public SystemController(
            SystemOverviewService systemOverviewService,
            BlockingOperationExecutor blockingOperationExecutor
    ) {
        this.systemOverviewService = systemOverviewService;
        this.blockingOperationExecutor = blockingOperationExecutor;
    }

    /**
     * 查询应用使用的基础设施隔离信息。
     *
     * @return 统一系统概览响应
     */
    @GetMapping("/overview")
    public Mono<ApiResponse<InfrastructureOverviewResponse>> getOverview() {
        return blockingOperationExecutor
                .execute(systemOverviewService::getOverview)
                .map(ApiResponse::success);
    }
}
