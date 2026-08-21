package com.dochelper.openapi.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.dochelper.openapi.domain.ApiEndpoint;
import com.dochelper.openapi.domain.EndpointDependencyEdge;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/**
 * 根据 OpenAPI 响应字段和请求输入推断生产者到消费者的候选依赖图。
 */
@Service
public class EndpointDependencyGraphService {

    private final OpenApiImportService openApiImportService;
    private final ObjectMapper objectMapper;

    public EndpointDependencyGraphService(
            OpenApiImportService openApiImportService,
            ObjectMapper objectMapper
    ) {
        this.openApiImportService = openApiImportService;
        this.objectMapper = objectMapper;
    }

    public List<EndpointDependencyEdge> build(Long projectId) {
        List<ApiEndpoint> endpoints = openApiImportService.listEndpoints(projectId, null);
        List<EndpointDependencyEdge> edges = new ArrayList<>();
        Set<String> uniqueKeys = new HashSet<>();
        for (ApiEndpoint producer : endpoints) {
            Map<String, String> outputs = fields(producer.responsesJson());
            for (ApiEndpoint consumer : endpoints) {
                if (producer.id().equals(consumer.id())) {
                    continue;
                }
                Map<String, String> inputs = new LinkedHashMap<>();
                consumer.parameters().forEach(parameter ->
                        inputs.put(normalize(parameter.name()), parameter.name())
                );
                inputs.putAll(fields(consumer.requestBodyJson()));
                outputs.forEach((normalized, original) -> {
                    if (!inputs.containsKey(normalized)) {
                        return;
                    }
                    String key = producer.id() + ":" + consumer.id() + ":" + normalized;
                    if (!uniqueKeys.add(key)) {
                        return;
                    }
                    String field = inputs.get(normalized);
                    boolean pathVariable = consumer.path().toLowerCase(Locale.ROOT)
                            .contains("{" + field.toLowerCase(Locale.ROOT) + "}");
                    edges.add(new EndpointDependencyEdge(
                            producer.id(), producer.operationId(), consumer.id(),
                            consumer.operationId(), field,
                            pathVariable ? 1.0 : normalized.endsWith("id") ? 0.9 : 0.7,
                            pathVariable ? "响应字段可绑定后续路径参数" : "响应字段与后续请求输入同名"
                    ));
                });
            }
        }
        return edges.stream()
                .sorted(Comparator.comparingDouble(EndpointDependencyEdge::confidence).reversed()
                        .thenComparing(EndpointDependencyEdge::producerEndpointId)
                        .thenComparing(EndpointDependencyEdge::consumerEndpointId)
                        .thenComparing(EndpointDependencyEdge::sharedField))
                .toList();
    }

    private Map<String, String> fields(String json) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return fields;
        }
        try {
            collectFields(objectMapper.readTree(json), fields);
            return fields;
        } catch (JsonProcessingException exception) {
            return fields;
        }
    }

    private void collectFields(JsonNode node, Map<String, String> fields) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            JsonNode properties = node.get("properties");
            if (properties != null && properties.isObject()) {
                properties.fieldNames().forEachRemaining(name ->
                        fields.putIfAbsent(normalize(name), name)
                );
            }
            node.elements().forEachRemaining(child -> collectFields(child, fields));
        } else if (node.isArray()) {
            node.elements().forEachRemaining(child -> collectFields(child, fields));
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "")
                .toLowerCase(Locale.ROOT);
    }
}
