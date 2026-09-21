package com.dochelper.common.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * 验证动态事件载荷中的 ID 精度保护。
 */
class BrowserSafeJsonIdsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldConvertNestedIdFieldsAndKeepMetricsNumeric() throws Exception {
        var source = objectMapper.readTree("""
                {
                  "environmentId": 2101971884483649538,
                  "durationMs": 178,
                  "items": [
                    {"id": 2101979996695404547, "score": 59}
                  ]
                }
                """);

        var result = BrowserSafeJsonIds.convert(source, objectMapper);

        assertThat(result.path("environmentId").asText()).isEqualTo("2101971884483649538");
        assertThat(result.path("environmentId").isTextual()).isTrue();
        assertThat(result.path("durationMs").isNumber()).isTrue();
        assertThat(result.path("items").path(0).path("id").isTextual()).isTrue();
        assertThat(result.path("items").path(0).path("score").isNumber()).isTrue();
    }
}
