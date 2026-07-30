package com.dochelper.executor.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.dochelper.common.exception.BusinessException;
import com.dochelper.executor.api.dto.ResponseAssertionRequest;
import com.dochelper.executor.api.dto.VariableExtractorRequest;
import com.dochelper.executor.domain.AssertionResult;
import com.dochelper.executor.domain.AssertionType;
import com.dochelper.executor.exception.ExecutionErrorCode;
import org.springframework.stereotype.Component;

/**
 * 使用 JSONPath 提取响应变量并执行结构化断言。
 */
@Component
public class JsonPathResponseProcessor {

    private final ObjectMapper objectMapper;

    public JsonPathResponseProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> extract(
            JsonNode body,
            List<VariableExtractorRequest> extractors
    ) {
        if (extractors == null || extractors.isEmpty()) {
            return Map.of();
        }
        DocumentContext context = parse(body);
        Map<String, Object> values = new LinkedHashMap<>();
        for (VariableExtractorRequest extractor : extractors) {
            try {
                values.put(extractor.name(), context.read(extractor.jsonPath()));
            } catch (InvalidPathException exception) {
                throw new BusinessException(
                        ExecutionErrorCode.INVALID_JSON_PATH,
                        "变量 " + extractor.name() + " 的 JSONPath 未命中：" + extractor.jsonPath()
                );
            }
        }
        return values;
    }

    public List<AssertionResult> assertResponse(
            int statusCode,
            JsonNode body,
            List<ResponseAssertionRequest> assertions
    ) {
        if (assertions == null || assertions.isEmpty()) {
            return List.of();
        }
        List<AssertionResult> results = new ArrayList<>();
        DocumentContext context = requiresJsonPath(assertions) ? parse(body) : null;
        for (ResponseAssertionRequest assertion : assertions) {
            results.add(evaluate(statusCode, context, assertion));
        }
        return List.copyOf(results);
    }

    private AssertionResult evaluate(
            int statusCode,
            DocumentContext context,
            ResponseAssertionRequest assertion
    ) {
        try {
            return switch (assertion.type()) {
                case STATUS_CODE -> statusAssertion(statusCode, assertion);
                case FIELD_EXISTS -> existsAssertion(context, assertion);
                case FIELD_NOT_EXISTS -> notExistsAssertion(context, assertion);
                case FIELD_TYPE -> typeAssertion(context, assertion);
                case FIELD_EQUALS -> equalsAssertion(context, assertion);
                case FIELD_CONTAINS -> containsAssertion(context, assertion);
            };
        } catch (InvalidPathException exception) {
            return result(assertion, false, expectedText(assertion), null, "JSONPath 未命中");
        }
    }

    private AssertionResult statusAssertion(
            int statusCode,
            ResponseAssertionRequest assertion
    ) {
        int expected = assertion.expectedValue() == null
                ? -1
                : assertion.expectedValue().asInt(-1);
        return result(
                assertion,
                statusCode == expected,
                String.valueOf(expected),
                String.valueOf(statusCode),
                statusCode == expected ? "状态码符合预期" : "状态码不符合预期"
        );
    }

    private AssertionResult existsAssertion(
            DocumentContext context,
            ResponseAssertionRequest assertion
    ) {
        Object actual = context.read(requirePath(assertion));
        return result(assertion, true, "存在", display(actual), "字段存在");
    }

    private AssertionResult notExistsAssertion(
            DocumentContext context,
            ResponseAssertionRequest assertion
    ) {
        try {
            Object actual = context.read(requirePath(assertion));
            boolean passed = actual == null;
            return result(
                    assertion,
                    passed,
                    "不存在",
                    display(actual),
                    passed ? "字段不存在或值为 null" : "字段不应存在"
            );
        } catch (PathNotFoundException exception) {
            return result(assertion, true, "不存在", null, "字段不存在");
        }
    }

    private AssertionResult typeAssertion(
            DocumentContext context,
            ResponseAssertionRequest assertion
    ) {
        Object actual = context.read(requirePath(assertion));
        String actualType = typeName(actual);
        String expectedType = assertion.expectedType() == null
                ? ""
                : assertion.expectedType().trim().toUpperCase();
        return result(
                assertion,
                actualType.equals(expectedType),
                expectedType,
                actualType,
                actualType.equals(expectedType) ? "字段类型符合预期" : "字段类型不符合预期"
        );
    }

    private AssertionResult equalsAssertion(
            DocumentContext context,
            ResponseAssertionRequest assertion
    ) {
        Object actual = context.read(requirePath(assertion));
        JsonNode actualNode = objectMapper.valueToTree(actual);
        boolean passed = Objects.equals(actualNode, assertion.expectedValue());
        return result(
                assertion,
                passed,
                expectedText(assertion),
                actualNode.toString(),
                passed ? "字段值符合预期" : "字段值不符合预期"
        );
    }

    private AssertionResult containsAssertion(
            DocumentContext context,
            ResponseAssertionRequest assertion
    ) {
        Object actual = context.read(requirePath(assertion));
        Object expected = assertion.expectedValue() == null
                ? null
                : objectMapper.convertValue(assertion.expectedValue(), Object.class);
        boolean passed;
        if (actual instanceof String text) {
            passed = text.contains(Objects.toString(expected, ""));
        } else if (actual instanceof Collection<?> collection) {
            passed = collection.contains(expected);
        } else {
            passed = false;
        }
        return result(
                assertion,
                passed,
                expectedText(assertion),
                display(actual),
                passed ? "字段包含预期值" : "字段不包含预期值"
        );
    }

    private DocumentContext parse(JsonNode body) {
        if (body == null || (!body.isObject() && !body.isArray())) {
            throw new BusinessException(
                    ExecutionErrorCode.INVALID_JSON_PATH,
                    "响应体不是可执行 JSONPath 的 JSON 对象或数组"
            );
        }
        return JsonPath.parse(body.toString());
    }

    private boolean requiresJsonPath(List<ResponseAssertionRequest> assertions) {
        return assertions.stream().anyMatch(assertion ->
                assertion.type() != AssertionType.STATUS_CODE
        );
    }

    private String requirePath(ResponseAssertionRequest assertion) {
        if (assertion.jsonPath() == null || assertion.jsonPath().isBlank()) {
            throw new BusinessException(
                    ExecutionErrorCode.INVALID_STEP,
                    assertion.type() + " 断言必须配置 JSONPath"
            );
        }
        return assertion.jsonPath();
    }

    private String typeName(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof String) {
            return "STRING";
        }
        if (value instanceof Number) {
            return "NUMBER";
        }
        if (value instanceof Boolean) {
            return "BOOLEAN";
        }
        if (value instanceof List<?>) {
            return "ARRAY";
        }
        if (value instanceof Map<?, ?>) {
            return "OBJECT";
        }
        return value.getClass().getSimpleName().toUpperCase();
    }

    private AssertionResult result(
            ResponseAssertionRequest assertion,
            boolean passed,
            String expected,
            String actual,
            String message
    ) {
        return new AssertionResult(
                assertion.type(),
                assertion.jsonPath(),
                passed,
                expected,
                actual,
                message
        );
    }

    private String expectedText(ResponseAssertionRequest assertion) {
        return assertion.expectedValue() == null ? null : assertion.expectedValue().toString();
    }

    private String display(Object value) {
        return value == null ? "null" : objectMapper.valueToTree(value).toString();
    }
}
