package com.dochelper.executor.application;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.dochelper.executor.api.dto.ResponseAssertionRequest;
import com.dochelper.executor.domain.AssertionType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证变量模板替换和 JSON 类型保留。
 */
class VariableTemplateRendererTest {

    private final VariableTemplateRenderer renderer = new VariableTemplateRenderer(
            JsonMapper.builder().build()
    );

    @Test
    void shouldRenderTextAndPreserveExactJsonValueType() throws Exception {
        JsonNode body = JsonMapper.builder().build().readTree("""
                {
                  "userId": "{{userId}}",
                  "message": "用户-{{userId}}",
                  "enabled": "{{enabled}}"
                }
                """);

        JsonNode rendered = renderer.renderJson(
                body,
                Map.of("userId", 42, "enabled", true)
        );

        assertThat(renderer.render("/users/{{userId}}", Map.of("userId", 42)))
                .isEqualTo("/users/42");
        assertThat(rendered.path("userId").isInt()).isTrue();
        assertThat(rendered.path("enabled").asBoolean()).isTrue();
        assertThat(rendered.path("message").asText()).isEqualTo("用户-42");
    }

    @Test
    void shouldRenderAssertionExpectedValueWithOriginalType() throws Exception {
        JsonNode expected = JsonMapper.builder().build()
                .readTree("\"{{username}}\"");
        ResponseAssertionRequest assertion = new ResponseAssertionRequest(
                AssertionType.FIELD_EQUALS,
                "$.data.{{fieldName}}",
                expected,
                null
        );

        ResponseAssertionRequest rendered = renderer.renderAssertions(
                java.util.List.of(assertion),
                Map.of(
                        "username", "stage7-user",
                        "fieldName", "username"
                )
        ).getFirst();

        assertThat(rendered.jsonPath()).isEqualTo("$.data.username");
        assertThat(rendered.expectedValue().asText()).isEqualTo("stage7-user");
    }
}
