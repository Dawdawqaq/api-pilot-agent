package com.dochelper.contract.application;

import java.util.List;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.dochelper.executor.domain.OperationRisk;
import com.dochelper.executor.domain.ResolvedOperation;
import com.dochelper.openapi.domain.ApiEndpoint;
import com.dochelper.openapi.domain.repository.OpenApiCatalogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证确定性响应 Schema 能发现缺失字段与类型错误。
 */
class OpenApiContractValidatorTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private OpenApiContractValidator validator;

    @BeforeEach
    void setUp() {
        OpenApiCatalogRepository repository = mock(OpenApiCatalogRepository.class);
        when(repository.findEndpoint(1L, 10L)).thenReturn(java.util.Optional.of(endpoint()));
        validator = new OpenApiContractValidator(repository, objectMapper);
    }

    @Test
    void shouldDetectMissingRequiredFieldAndWrongType() throws Exception {
        var result = validator.validate(
                1L,
                new ResolvedOperation(10L, "getUser", "GET", "/users/{id}", OperationRisk.READ_ONLY),
                200,
                objectMapper.readTree("{\"name\":123}")
        );

        assertThat(result.passed()).isFalse();
        assertThat(result.violations()).extracting("rule")
                .contains("REQUIRED", "TYPE");
    }

    @Test
    void shouldRejectUndocumentedStatusCode() throws Exception {
        var result = validator.validate(
                1L,
                new ResolvedOperation(10L, "getUser", "GET", "/users/{id}", OperationRisk.READ_ONLY),
                503,
                objectMapper.readTree("{}")
        );

        assertThat(result.violations()).extracting("rule").containsExactly("DOCUMENTED_STATUS");
    }

    private ApiEndpoint endpoint() {
        String responses = """
                {
                  "200": {
                    "description": "ok",
                    "content": {
                      "application/json": {
                        "schema": {
                          "type": "object",
                          "required": ["id", "name"],
                          "properties": {
                            "id": {"type": "integer"},
                            "name": {"type": "string"}
                          }
                        }
                      }
                    }
                  }
                }
                """;
        return new ApiEndpoint(
                10L, 1L, 2L, "/users/{id}", "GET", "getUser", null, null,
                "[]", false, null, responses, null, List.of()
        );
    }
}
