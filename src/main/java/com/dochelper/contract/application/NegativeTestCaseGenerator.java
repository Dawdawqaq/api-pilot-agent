package com.dochelper.contract.application;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dochelper.contract.api.vo.NegativeTestCaseResponse;
import com.dochelper.openapi.domain.ApiEndpoint;
import com.dochelper.openapi.domain.ParsedParameter;
import org.springframework.stereotype.Component;

/**
 * 从 OpenAPI 参数约束确定性生成基础负向用例。
 */
@Component
public class NegativeTestCaseGenerator {

    private final ObjectMapper objectMapper;

    public NegativeTestCaseGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<NegativeTestCaseResponse> generate(ApiEndpoint endpoint) {
        List<NegativeTestCaseResponse> cases = new ArrayList<>();
        for (ParsedParameter parameter : endpoint.parameters()) {
            JsonNode schema = parse(parameter.schemaJson());
            if (parameter.required()) {
                cases.add(new NegativeTestCaseResponse(
                        "MISSING_REQUIRED", parameter.location() + ":" + parameter.name(),
                        "删除必填参数", "返回文档声明的 4xx，且不能出现 5xx"
                ));
            }
            if (schema.path("enum").isArray()) {
                cases.add(new NegativeTestCaseResponse(
                        "INVALID_ENUM", parameter.location() + ":" + parameter.name(),
                        "替换为枚举范围外的值 __INVALID__", "返回参数校验失败"
                ));
            }
            if (schema.has("maxLength") || schema.has("minLength")) {
                cases.add(new NegativeTestCaseResponse(
                        "BOUNDARY_LENGTH", parameter.location() + ":" + parameter.name(),
                        "生成长度边界值及越界值", "边界值通过，越界值被拒绝"
                ));
            }
            if (schema.has("type")) {
                cases.add(new NegativeTestCaseResponse(
                        "WRONG_TYPE", parameter.location() + ":" + parameter.name(),
                        "替换为与 " + schema.path("type").asText() + " 不兼容的 JSON 类型",
                        "返回参数类型错误"
                ));
            }
        }
        return List.copyOf(cases);
    }

    private JsonNode parse(String value) {
        if (value == null || value.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            return objectMapper.createObjectNode();
        }
    }
}
