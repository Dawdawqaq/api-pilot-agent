package com.dochelper.openapi.domain;

/**
 * 基于响应字段与后续请求输入推断出的接口依赖候选。
 */
public record EndpointDependencyEdge(
        Long producerEndpointId,
        String producerOperationId,
        Long consumerEndpointId,
        String consumerOperationId,
        String sharedField,
        double confidence,
        String reason
) {
}
