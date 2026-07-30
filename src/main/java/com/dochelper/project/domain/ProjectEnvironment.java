package com.dochelper.project.domain;

import java.time.LocalDateTime;

/**
 * 被测项目环境领域模型。
 *
 * @param id 环境标识
 * @param projectId 项目标识
 * @param name 环境名称
 * @param baseUrl 被测服务基础地址
 * @param allowedMethods 允许执行的 HTTP 方法
 * @param allowPrivateNetwork 是否允许访问私网或环回地址
 * @param defaultEnvironment 是否默认环境
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record ProjectEnvironment(
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
}
