package com.dochelper.common.json;

import java.util.ArrayList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 规范化对象键顺序，保证等价请求在模型重排字段后仍使用同一恢复指纹。
 */
public final class CanonicalJson {
    private CanonicalJson() { }

    public static JsonNode normalize(JsonNode node, ObjectMapper mapper) {
        if (node.isObject()) {
            var result = mapper.createObjectNode();
            var fields = new ArrayList<String>();
            node.fieldNames().forEachRemaining(fields::add);
            fields.stream().sorted().forEach(name -> result.set(name, normalize(node.get(name), mapper)));
            return result;
        }
        if (node.isArray()) {
            var result = mapper.createArrayNode();
            node.forEach(item -> result.add(normalize(item, mapper)));
            return result;
        }
        return node;
    }
}
