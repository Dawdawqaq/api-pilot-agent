package com.dochelper.project.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建项目环境请求。
 *
 * @param name 环境名称
 * @param baseUrl 被测服务基础地址
 * @param allowedMethods 允许执行的 HTTP 方法，逗号分隔
 * @param allowPrivateNetwork 是否显式允许私网或环回地址
 * @param defaultEnvironment 是否设置为默认环境
 */
public record CreateEnvironmentRequest(
        @NotBlank @Size(max = 64) String name,
        @NotBlank @Size(max = 512) String baseUrl,
        @Size(max = 128) String allowedMethods,
        Boolean allowPrivateNetwork,
        boolean defaultEnvironment
) {
}
