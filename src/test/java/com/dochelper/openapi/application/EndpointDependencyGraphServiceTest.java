package com.dochelper.openapi.application;

import java.util.List;

import com.dochelper.openapi.domain.ApiEndpoint;
import com.dochelper.openapi.domain.ParsedParameter;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证响应字段能够推断为后续路径参数或请求体输入。
 */
class EndpointDependencyGraphServiceTest {

    @Test
    void shouldInferProducerConsumerEdgesFromSharedFields() {
        OpenApiImportService importService = mock(OpenApiImportService.class);
        when(importService.listEndpoints(1L, null)).thenReturn(List.of(
                endpoint(
                        1L, "/orders", "POST", "createOrder", List.of(),
                        null,
                        "{\"200\":{\"content\":{\"application/json\":{\"schema\":{\"type\":\"object\",\"properties\":{\"orderId\":{\"type\":\"integer\"},\"status\":{\"type\":\"string\"}}}}}}}"
                ),
                endpoint(
                        2L, "/orders/{orderId}", "GET", "getOrder",
                        List.of(new ParsedParameter("orderId", "path", true, null, "{}")),
                        null,
                        "{}"
                ),
                endpoint(
                        3L, "/payments", "POST", "payOrder", List.of(),
                        "{\"content\":{\"application/json\":{\"schema\":{\"properties\":{\"orderId\":{\"type\":\"integer\"}}}}}}",
                        "{}"
                )
        ));
        EndpointDependencyGraphService service = new EndpointDependencyGraphService(
                importService,
                JsonMapper.builder().build()
        );

        var edges = service.build(1L);

        assertThat(edges).extracting(edge -> edge.consumerOperationId())
                .containsExactlyInAnyOrder("getOrder", "payOrder");
        assertThat(edges.stream()
                .filter(edge -> "getOrder".equals(edge.consumerOperationId()))
                .findFirst().orElseThrow().confidence()).isEqualTo(1.0);
    }

    private ApiEndpoint endpoint(
            Long id,
            String path,
            String method,
            String operationId,
            List<ParsedParameter> parameters,
            String requestBody,
            String responses
    ) {
        return new ApiEndpoint(
                id, 1L, 1L, path, method, operationId, operationId, null,
                "[]", false, requestBody, responses, null, parameters
        );
    }
}
