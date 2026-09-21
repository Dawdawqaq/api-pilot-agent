package com.dochelper.agent.application;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

import com.dochelper.openapi.domain.ApiEndpoint;
import com.dochelper.openapi.domain.repository.OpenApiCatalogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * 收集候选接口的本地 Schema 引用闭包，避免模型只看到引用名而猜测响应字段。
 */
@Component
public class PlanningSchemaResolver {
    private static final String PREFIX = "#/components/schemas/";
    private static final int MAX_SCHEMAS = 64;
    private final OpenApiCatalogRepository catalog;
    private final ObjectMapper mapper;

    public PlanningSchemaResolver(OpenApiCatalogRepository catalog, ObjectMapper mapper) {
        this.catalog = catalog;
        this.mapper = mapper;
    }

    public Map<String, JsonNode> collect(List<ApiEndpoint> endpoints) {
        Map<String, JsonNode> schemas = new LinkedHashMap<>();
        Set<String> visited = new LinkedHashSet<>();
        for (ApiEndpoint endpoint : endpoints) {
            var queue = new ArrayDeque<String>();
            collectReferences(parse(endpoint.requestBodyJson()), queue);
            collectReferences(parse(endpoint.responsesJson()), queue);
            if (endpoint.parameters() != null) {
                endpoint.parameters().forEach(parameter -> collectReferences(parse(parameter.schemaJson()), queue));
            }
            while (!queue.isEmpty()) {
                String name = queue.removeFirst();
                String key = endpoint.importId() + ":" + PREFIX + name;
                if (!visited.add(key)) {
                    continue;
                }
                if (visited.size() > MAX_SCHEMAS) {
                    throw new com.dochelper.common.exception.BusinessException(
                            com.dochelper.agent.exception.AgentErrorCode.PLANNING_FAILED,
                            "候选接口引用的 Schema 过多，请缩小任务范围");
                }
                String schemaName = name.replace("~1", "/").replace("~0", "~");
                catalog.findSchemaJson(endpoint.projectId(), endpoint.importId(), schemaName).ifPresent(raw -> {
                    JsonNode schema = parse(raw);
                    schemas.put(key, schema);
                    collectReferences(schema, queue);
                });
            }
        }
        return schemas;
    }

    private JsonNode parse(String value) {
        if (value == null || value.isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            return mapper.readTree(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("接口目录 Schema JSON 损坏", exception);
        }
    }

    private void collectReferences(JsonNode node, ArrayDeque<String> queue) {
        String reference = node.path("$ref").asText("");
        if (reference.startsWith(PREFIX)) {
            queue.add(reference.substring(PREFIX.length()));
        }
        // 外部引用不发起网络访问，循环引用由已访问集合消除。
        node.elements().forEachRemaining(child -> collectReferences(child, queue));
    }
}
