package com.dochelper.openapi.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.dochelper.openapi.domain.ParsedOpenApiDocument;
import com.dochelper.openapi.exception.OpenApiParseException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 OpenAPI JSON/YAML 解析及错误边界。
 */
class OpenApiDocumentParserTest {

    private final OpenApiDocumentParser parser = new OpenApiDocumentParser();

    @Test
    void shouldParseJApiServerYamlFixture() throws IOException {
        String content = new ClassPathResource("openapi/japiserver-stage2.yaml")
                .getContentAsString(StandardCharsets.UTF_8);

        ParsedOpenApiDocument document = parser.parse(content);

        assertThat(document.specificationVersion()).isEqualTo("3.0.3");
        assertThat(document.title()).contains("JApiServer");
        assertThat(document.endpoints()).hasSize(4);
        assertThat(document.schemas()).hasSize(3);
        assertThat(document.securitySchemes()).hasSize(1);
        assertThat(document.endpoints())
                .filteredOn(endpoint -> endpoint.operationId().equals("deleteDomain"))
                .singleElement()
                .satisfies(endpoint -> {
                    assertThat(endpoint.httpMethod()).isEqualTo("DELETE");
                    assertThat(endpoint.parameters()).hasSize(1);
                    assertThat(endpoint.parameters().getFirst().required()).isTrue();
                });
    }

    @Test
    void shouldConvertSwaggerTwoDocument() {
        String swagger = """
                {
                  "swagger": "2.0",
                  "info": {"title": "旧版接口", "version": "1.0"},
                  "paths": {
                    "/health": {
                      "get": {
                        "operationId": "health",
                        "responses": {"200": {"description": "正常"}}
                      }
                    }
                  }
                }
                """;

        ParsedOpenApiDocument document = parser.parse(swagger);

        assertThat(document.endpoints()).hasSize(1);
        assertThat(document.endpoints().getFirst().operationId()).isEqualTo("health");
    }

    @Test
    void shouldRejectDocumentWithoutPaths() {
        assertThatThrownBy(() -> parser.parse("""
                openapi: 3.0.3
                info:
                  title: 空文档
                  version: 1.0.0
                paths: {}
                """))
                .isInstanceOf(OpenApiParseException.class)
                .hasMessageContaining("接口路径");
    }
}
