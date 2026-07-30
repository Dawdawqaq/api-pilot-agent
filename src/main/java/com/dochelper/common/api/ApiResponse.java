package com.dochelper.common.api;

import java.time.Instant;

/**
 * 统一 API 响应结构。
 *
 * @param code 业务响应码
 * @param message 响应说明
 * @param data 响应数据
 * @param requestId 请求追踪标识
 * @param timestamp 响应时间
 * @param <T> 数据类型
 */
public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String requestId,
        Instant timestamp
) {

    private static final String SUCCESS_CODE = "SUCCESS";

    /**
     * 创建成功响应。
     *
     * @param data 响应数据
     * @param <T> 数据类型
     * @return 统一成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_CODE, "请求成功", data, null, Instant.now());
    }

    /**
     * 创建失败响应。
     *
     * @param code 错误码
     * @param message 错误说明
     * @param requestId 请求追踪标识
     * @return 统一失败响应
     */
    public static ApiResponse<Void> failure(String code, String message, String requestId) {
        return new ApiResponse<>(code, message, null, requestId, Instant.now());
    }
}
