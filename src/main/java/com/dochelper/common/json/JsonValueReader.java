package com.dochelper.common.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 将数据库中的 JSON 文本转换为接口响应节点。
 */
public class JsonValueReader {

    private final ObjectMapper objectMapper;

    public JsonValueReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode read(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("数据库中的 JSON 数据损坏", exception);
        }
    }
}
