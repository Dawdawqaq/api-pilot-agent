package com.dochelper.contract.application;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dochelper.contract.domain.ContractValidationResult;
import com.dochelper.contract.domain.ContractViolation;
import com.dochelper.executor.domain.ResolvedOperation;
import com.dochelper.openapi.domain.ApiEndpoint;
import com.dochelper.openapi.domain.repository.OpenApiCatalogRepository;
import org.springframework.stereotype.Component;

/**
 * 不依赖大模型的 OpenAPI 响应状态码与 JSON Schema 校验器。
 */
@Component
public class OpenApiContractValidator {

    private final OpenApiCatalogRepository catalogRepository;
    private final ObjectMapper objectMapper;

    public OpenApiContractValidator(
            OpenApiCatalogRepository catalogRepository,
            ObjectMapper objectMapper
    ) {
        this.catalogRepository = catalogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 校验响应状态码、必填字段、类型、枚举、格式与数组结构。
     */
    public ContractValidationResult validate(
            Long projectId,
            ResolvedOperation operation,
            int responseStatus,
            JsonNode responseBody
    ) {
        ApiEndpoint endpoint = catalogRepository.findEndpoint(projectId, operation.endpointId())
                .orElseThrow(() -> new IllegalStateException("契约校验时接口目录已不存在"));
        List<ContractViolation> violations = new ArrayList<>();
        RuleCounter counter = new RuleCounter();
        counter.add();
        JsonNode responses = parse(endpoint.responsesJson());
        JsonNode response = responses.path(String.valueOf(responseStatus));
        if (response.isMissingNode()) {
            response = responses.path("default");
        }
        if (response.isMissingNode()) {
            violations.add(new ContractViolation(
                    "$status", "DOCUMENTED_STATUS", "响应状态码未在 OpenAPI 中声明：" + responseStatus
            ));
            return new ContractValidationResult(counter.total, 0, List.copyOf(violations));
        }
        JsonNode schema = selectResponseSchema(response);
        if (schema == null || schema.isMissingNode() || responseBody == null) {
            return new ContractValidationResult(counter.total, counter.total, List.of());
        }
        validateNode(
                projectId,
                endpoint.importId(),
                resolveSchema(projectId, endpoint.importId(), schema),
                responseBody,
                "$",
                counter,
                violations,
                0
        );
        return new ContractValidationResult(
                counter.total,
                Math.max(0, counter.total - violations.size()),
                List.copyOf(violations)
        );
    }

    private void validateNode(
            Long projectId,
            Long importId,
            JsonNode rawSchema,
            JsonNode value,
            String path,
            RuleCounter counter,
            List<ContractViolation> violations,
            int depth
    ) {
        if (depth > 20 || rawSchema == null || rawSchema.isMissingNode()) {
            return;
        }
        JsonNode schema = resolveSchema(projectId, importId, rawSchema);
        String type = schema.path("type").asText("");
        if (!type.isBlank()) {
            counter.add();
            if (!matchesType(type, value)) {
                violations.add(new ContractViolation(
                        path, "TYPE", "期望类型 " + type + "，实际为 " + nodeType(value)
                ));
                return;
            }
        }
        JsonNode enumValues = schema.path("enum");
        if (enumValues.isArray()) {
            counter.add();
            boolean matched = java.util.stream.StreamSupport.stream(enumValues.spliterator(), false)
                    .anyMatch(value::equals);
            if (!matched) {
                violations.add(new ContractViolation(path, "ENUM", "响应值不在声明的枚举范围内"));
            }
        }
        String format = schema.path("format").asText("");
        if (!format.isBlank() && value.isTextual()) {
            counter.add();
            if (!validFormat(format, value.asText())) {
                violations.add(new ContractViolation(path, "FORMAT", "字符串不符合 " + format + " 格式"));
            }
        }
        if (value.isObject()) {
            JsonNode required = schema.path("required");
            if (required.isArray()) {
                required.forEach(name -> {
                    counter.add();
                    if (!value.has(name.asText()) || value.get(name.asText()).isNull()) {
                        violations.add(new ContractViolation(
                                path + "." + name.asText(), "REQUIRED", "缺少必填字段"
                        ));
                    }
                });
            }
            Iterator<String> fieldNames = schema.path("properties").fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                if (value.has(fieldName) && !value.get(fieldName).isNull()) {
                    validateNode(
                            projectId, importId, schema.path("properties").path(fieldName),
                            value.get(fieldName), path + "." + fieldName,
                            counter, violations, depth + 1
                    );
                }
            }
        }
        if (value.isArray() && schema.has("items")) {
            counter.add();
            for (int index = 0; index < value.size(); index++) {
                validateNode(
                        projectId, importId, schema.path("items"), value.get(index),
                        path + "[" + index + "]", counter, violations, depth + 1
                );
            }
        }
    }

    private JsonNode selectResponseSchema(JsonNode response) {
        JsonNode content = response.path("content");
        JsonNode media = content.path("application/json");
        if (media.isMissingNode()) {
            Iterator<JsonNode> values = content.elements();
            media = values.hasNext() ? values.next() : null;
        }
        return media == null ? null : media.path("schema");
    }

    private JsonNode resolveSchema(Long projectId, Long importId, JsonNode schema) {
        String reference = schema.path("$ref").asText("");
        String prefix = "#/components/schemas/";
        if (!reference.startsWith(prefix)) {
            return schema;
        }
        String schemaName = reference.substring(prefix.length());
        return catalogRepository.findSchemaJson(projectId, importId, schemaName)
                .map(this::parse)
                .orElse(schema);
    }

    private boolean matchesType(String type, JsonNode value) {
        return switch (type) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "null" -> value.isNull();
            default -> true;
        };
    }

    private boolean validFormat(String format, String value) {
        try {
            return switch (format) {
                case "uuid" -> UUID.fromString(value) != null;
                case "date-time" -> OffsetDateTime.parse(value) != null;
                case "email" -> value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
                default -> true;
            };
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String nodeType(JsonNode value) {
        return value == null ? "missing" : value.getNodeType().name().toLowerCase();
    }

    private JsonNode parse(String value) {
        if (value == null || value.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("OpenAPI 契约 JSON 数据损坏", exception);
        }
    }

    private static final class RuleCounter {
        private int total;
        private void add() { total++; }
    }
}
