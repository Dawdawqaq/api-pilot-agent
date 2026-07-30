package com.dochelper.executor.application;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.dochelper.executor.api.dto.ResponseAssertionRequest;
import com.dochelper.executor.api.dto.VariableExtractorRequest;
import com.dochelper.executor.domain.AssertionResult;
import com.dochelper.executor.domain.AssertionType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 JSONPath 变量提取和六类响应断言。
 */
class JsonPathResponseProcessorTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final JsonPathResponseProcessor processor = new JsonPathResponseProcessor(objectMapper);

    @Test
    void shouldExtractVariableAndEvaluateAssertions() throws Exception {
        JsonNode body = objectMapper.readTree("""
                {
                  "data": {
                    "token": "secret-token",
                    "roles": ["USER", "ADMIN"],
                    "active": true
                  }
                }
                """);
        List<ResponseAssertionRequest> assertions = List.of(
                new ResponseAssertionRequest(
                        AssertionType.STATUS_CODE, null, objectMapper.valueToTree(200), null
                ),
                new ResponseAssertionRequest(
                        AssertionType.FIELD_EXISTS, "$.data.token", null, null
                ),
                new ResponseAssertionRequest(
                        AssertionType.FIELD_NOT_EXISTS, "$.data.refreshToken", null, null
                ),
                new ResponseAssertionRequest(
                        AssertionType.FIELD_TYPE, "$.data.active", null, "BOOLEAN"
                ),
                new ResponseAssertionRequest(
                        AssertionType.FIELD_EQUALS,
                        "$.data.active",
                        objectMapper.valueToTree(true),
                        null
                ),
                new ResponseAssertionRequest(
                        AssertionType.FIELD_CONTAINS,
                        "$.data.roles",
                        objectMapper.valueToTree("ADMIN"),
                        null
                )
        );

        List<AssertionResult> results = processor.assertResponse(200, body, assertions);

        assertThat(results).allMatch(AssertionResult::passed);
        assertThat(processor.extract(
                body,
                List.of(new VariableExtractorRequest("accessToken", "$.data.token"))
        )).containsEntry("accessToken", "secret-token");
    }

    @Test
    void shouldTreatNullAndMissingPathsAsNotExisting() throws Exception {
        JsonNode body = objectMapper.readTree("""
                {
                  "data": null
                }
                """);
        List<ResponseAssertionRequest> assertions = List.of(
                new ResponseAssertionRequest(
                        AssertionType.FIELD_NOT_EXISTS,
                        "$.data",
                        null,
                        null
                ),
                new ResponseAssertionRequest(
                        AssertionType.FIELD_NOT_EXISTS,
                        "$.data.token",
                        null,
                        null
                )
        );

        assertThat(processor.assertResponse(401, body, assertions))
                .allMatch(AssertionResult::passed);
    }
}
