package com.dochelper.executor.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.dochelper.common.exception.BusinessException;
import com.dochelper.executor.api.dto.ResponseAssertionRequest;
import com.dochelper.executor.exception.ExecutionErrorCode;
import org.springframework.stereotype.Component;

/**
 * 将场景变量安全渲染到路径、查询参数、请求头和 JSON 请求体。
 */
@Component
public class VariableTemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9_]{0,63})}}");

    private final ObjectMapper objectMapper;

    public VariableTemplateRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String render(String template, Map<String, Object> variables) {
        if (template == null) {
            return null;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            Object value = requireVariable(variables, matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(Objects.toString(value, "")));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public Map<String, String> renderMap(
            Map<String, String> source,
            Map<String, Object> variables
    ) {
        if (source == null || source.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, String> rendered = new LinkedHashMap<>();
        source.forEach((key, value) -> rendered.put(key, render(value, variables)));
        return rendered;
    }

    public JsonNode renderJson(JsonNode source, Map<String, Object> variables) {
        if (source == null || source.isNull()) {
            return source;
        }
        if (source.isObject()) {
            ObjectNode target = ((ObjectNode) source).deepCopy();
            target.fields().forEachRemaining(entry ->
                    target.set(entry.getKey(), renderJson(entry.getValue(), variables))
            );
            return target;
        }
        if (source.isArray()) {
            ArrayNode target = ((ArrayNode) source).deepCopy();
            for (int index = 0; index < target.size(); index++) {
                target.set(index, renderJson(target.get(index), variables));
            }
            return target;
        }
        if (!source.isTextual()) {
            return source.deepCopy();
        }
        String text = source.textValue();
        Matcher exact = PLACEHOLDER.matcher(text);
        if (exact.matches()) {
            Object value = requireVariable(variables, exact.group(1));
            return objectMapper.valueToTree(value);
        }
        return TextNode.valueOf(render(text, variables));
    }

    /**
     * 渲染响应断言中的 JSONPath 和预期值，同时保留 JSON 原始类型。
     *
     * @param assertions 原始断言
     * @param variables 场景变量
     * @return 渲染后的断言
     */
    public java.util.List<ResponseAssertionRequest> renderAssertions(
            java.util.List<ResponseAssertionRequest> assertions,
            Map<String, Object> variables
    ) {
        if (assertions == null || assertions.isEmpty()) {
            return java.util.List.of();
        }
        return assertions.stream()
                .map(assertion -> new ResponseAssertionRequest(
                        assertion.type(),
                        render(assertion.jsonPath(), variables),
                        renderJson(assertion.expectedValue(), variables),
                        assertion.expectedType()
                ))
                .toList();
    }

    private Object requireVariable(Map<String, Object> variables, String name) {
        if (variables == null || !variables.containsKey(name)) {
            throw new BusinessException(
                    ExecutionErrorCode.VARIABLE_NOT_FOUND,
                    "模板变量不存在：" + name
            );
        }
        return variables.get(name);
    }
}
