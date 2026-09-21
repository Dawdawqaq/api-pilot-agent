package com.dochelper.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * 验证雪花 ID 的浏览器安全序列化规则。
 */
class JacksonIdSerializationConfigurationTest {

    @Test
    void shouldSerializeLongIdsAsStringsAndKeepMetricsNumeric() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(
                new JacksonIdSerializationConfiguration().idSerializationModule()
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(
                new SampleResponse(
                        2101915029883322369L,
                        2101915030231449601L,
                        306L,
                        1
                )
        ));

        assertThat(json.path("id").isTextual()).isTrue();
        assertThat(json.path("id").asText()).isEqualTo("2101915029883322369");
        assertThat(json.path("projectId").isTextual()).isTrue();
        assertThat(json.path("durationMs").isNumber()).isTrue();
        assertThat(json.path("stepCount").isNumber()).isTrue();
    }

    private record SampleResponse(
            Long id,
            Long projectId,
            Long durationMs,
            int stepCount
    ) {
    }
}
