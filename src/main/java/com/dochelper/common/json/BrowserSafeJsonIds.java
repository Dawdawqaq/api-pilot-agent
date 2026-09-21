package com.dochelper.common.json;

import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 将动态 JSON 结构中的长整型 ID 转为字符串，避免浏览器解析时丢失精度。
 */
public final class BrowserSafeJsonIds {

    private BrowserSafeJsonIds() {
    }

    /**
     * 递归复制 JSON，并把名称为 id 或以 Id 结尾的整数属性转为字符串。
     *
     * @param node 原始 JSON
     * @param objectMapper JSON 对象映射器
     * @return 浏览器可安全读取的 JSON
     */
    public static JsonNode convert(JsonNode node, ObjectMapper objectMapper) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isObject()) {
            var result = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode value = field.getValue();
                if (isIdName(field.getKey()) && value != null && value.isIntegralNumber()) {
                    result.put(field.getKey(), value.asText());
                } else {
                    result.set(field.getKey(), convert(value, objectMapper));
                }
            }
            return result;
        }
        if (node.isArray()) {
            var result = objectMapper.createArrayNode();
            node.forEach(item -> result.add(convert(item, objectMapper)));
            return result;
        }
        return node;
    }

    private static boolean isIdName(String name) {
        return "id".equals(name) || name.endsWith("Id");
    }
}
