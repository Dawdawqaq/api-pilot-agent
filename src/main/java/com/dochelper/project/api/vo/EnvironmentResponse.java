package com.dochelper.project.api.vo;

import java.time.LocalDateTime;

import com.dochelper.project.domain.ProjectEnvironment;

/**
 * 项目环境响应。
 *
 * @param id 环境标识
 * @param projectId 项目标识
 * @param name 环境名称
 * @param baseUrl 基础地址
 * @param allowedMethods 允许执行的 HTTP 方法
 * @param allowPrivateNetwork 是否允许访问私网或环回地址
 * @param defaultEnvironment 是否默认环境
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record EnvironmentResponse(
        Long id,
        Long projectId,
        String name,
        String baseUrl,
        String allowedMethods,
        boolean allowPrivateNetwork,
        boolean defaultEnvironment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static EnvironmentResponse from(ProjectEnvironment environment) {
        return new EnvironmentResponse(
                environment.id(),
                environment.projectId(),
                environment.name(),
                environment.baseUrl(),
                environment.allowedMethods(),
                environment.allowPrivateNetwork(),
                environment.defaultEnvironment(),
                environment.createdAt(),
                environment.updatedAt()
        );
    }
}
