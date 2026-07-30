package com.dochelper.executor.application;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.dochelper.executor.config.ExecutorProperties;
import org.springframework.stereotype.Component;

/**
 * 对请求头、JSON 字段和提取变量中的敏感值进行脱敏。
 */
@Component
public class SensitiveDataSanitizer {

    private static final String MASK = "******";
    private static final Pattern BEARER_VALUE = Pattern.compile(
            "(?i)(Bearer\\s+)[^\\s,;]+"
    );
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)((?:token|password|secret|api[-_]?key)\\s*[:=]\\s*)[^\\s,;]+"
    );

    private final Pattern sensitiveNamePattern;

    public SensitiveDataSanitizer(ExecutorProperties properties) {
        this.sensitiveNamePattern = Pattern.compile(properties.sensitiveNamePattern());
    }

    public Map<String, String> sanitizeHeaders(Map<String, String> headers) {
        Map<String, String> sanitized = new LinkedHashMap<>();
        headers.forEach((name, value) ->
                sanitized.put(name, isSensitive(name) ? MASK : value)
        );
        return sanitized;
    }

    public Map<String, List<String>> sanitizeResponseHeaders(Map<String, List<String>> headers) {
        Map<String, List<String>> sanitized = new LinkedHashMap<>();
        headers.forEach((name, values) ->
                sanitized.put(name, isSensitive(name) ? List.of(MASK) : List.copyOf(values))
        );
        return sanitized;
    }

    public Map<String, Object> sanitizeVariables(Map<String, Object> variables) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        variables.forEach((name, value) ->
                sanitized.put(name, isSensitive(name) ? MASK : value)
        );
        return sanitized;
    }

    public JsonNode sanitizeJson(JsonNode source) {
        return sanitizeJson(null, source);
    }

    private JsonNode sanitizeJson(String fieldName, JsonNode source) {
        if (source == null || source.isNull()) {
            return source;
        }
        if (fieldName != null && isSensitive(fieldName)) {
            return TextNode.valueOf(MASK);
        }
        if (source.isObject()) {
            ObjectNode target = ((ObjectNode) source).deepCopy();
            target.fields().forEachRemaining(entry ->
                    target.set(entry.getKey(), sanitizeJson(entry.getKey(), entry.getValue()))
            );
            return target;
        }
        if (source.isArray()) {
            ArrayNode target = ((ArrayNode) source).deepCopy();
            for (int index = 0; index < target.size(); index++) {
                target.set(index, sanitizeJson(fieldName, target.get(index)));
            }
            return target;
        }
        return source.deepCopy();
    }

    public boolean isSensitive(String name) {
        return name != null && sensitiveNamePattern.matcher(name).matches();
    }

    /**
     * 对自然语言中的常见 Bearer 和密钥赋值片段进行兜底脱敏。
     *
     * @param value 原始文本
     * @return 脱敏文本
     */
    public String sanitizeText(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String sanitized = BEARER_VALUE.matcher(value).replaceAll("$1" + MASK);
        return SECRET_ASSIGNMENT.matcher(sanitized).replaceAll("$1" + MASK);
    }

    /**
     * 审计地址保留查询参数名，但统一隐藏查询参数值。
     */
    public String sanitizeUrl(String value) {
        try {
            URI uri = URI.create(value);
            String query = uri.getRawQuery();
            String sanitizedQuery = null;
            if (query != null && !query.isBlank()) {
                sanitizedQuery = java.util.Arrays.stream(query.split("&"))
                        .map(pair -> {
                            int separator = pair.indexOf('=');
                            String name = separator < 0 ? pair : pair.substring(0, separator);
                            return name + "=" + MASK;
                        })
                        .collect(java.util.stream.Collectors.joining("&"));
            }
            return new URI(
                    uri.getScheme(),
                    uri.getAuthority(),
                    uri.getPath(),
                    sanitizedQuery,
                    null
            ).toString();
        } catch (IllegalArgumentException | URISyntaxException exception) {
            return "地址已隐藏";
        }
    }
}
