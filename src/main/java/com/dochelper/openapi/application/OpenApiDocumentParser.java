package com.dochelper.openapi.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.dochelper.openapi.domain.ParsedEndpoint;
import com.dochelper.openapi.domain.ParsedOpenApiDocument;
import com.dochelper.openapi.domain.ParsedParameter;
import com.dochelper.openapi.domain.ParsedSchema;
import com.dochelper.openapi.domain.ParsedSecurityScheme;
import com.dochelper.openapi.exception.OpenApiParseException;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.parser.converter.SwaggerConverter;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.springframework.stereotype.Component;

/**
 * 将 JSON/YAML OpenAPI 文档转换为项目内部结构。
 */
@Component
public class OpenApiDocumentParser {

    /**
     * 解析 OpenAPI 文本。禁止解析外部引用，避免导入过程访问不受信任网络。
     *
     * @param content OpenAPI 原始文本
     * @return 结构化解析结果
     */
    public ParsedOpenApiDocument parse(String content) {
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        options.setResolveCombinators(false);

        SwaggerParseResult result;
        try {
            boolean swaggerTwo = Yaml.mapper().readTree(content).has("swagger");
            result = swaggerTwo
                    ? new SwaggerConverter().readContents(content, null, options)
                    : new OpenAPIV3Parser().readContents(content, null, options);
        } catch (RuntimeException exception) {
            throw new OpenApiParseException("文档不是合法的 OpenAPI JSON/YAML", exception);
        } catch (Exception exception) {
            throw new OpenApiParseException("文档不是合法的 OpenAPI JSON/YAML", exception);
        }

        OpenAPI openApi = result.getOpenAPI();
        if (openApi == null || openApi.getPaths() == null || openApi.getPaths().isEmpty()) {
            throw new OpenApiParseException(formatMessages(result.getMessages()));
        }

        List<ParsedEndpoint> endpoints = parseEndpoints(openApi);
        List<ParsedSchema> schemas = parseSchemas(openApi);
        List<ParsedSecurityScheme> securitySchemes = parseSecuritySchemes(openApi);
        String specificationVersion = openApi.getOpenapi() == null ? "unknown" : openApi.getOpenapi();
        String title = openApi.getInfo() == null ? null : openApi.getInfo().getTitle();
        String version = openApi.getInfo() == null ? null : openApi.getInfo().getVersion();

        return new ParsedOpenApiDocument(
                specificationVersion,
                title,
                version,
                endpoints,
                schemas,
                securitySchemes,
                result.getMessages() == null ? List.of() : List.copyOf(result.getMessages())
        );
    }

    private List<ParsedEndpoint> parseEndpoints(OpenAPI openApi) {
        List<ParsedEndpoint> endpoints = new ArrayList<>();
        openApi.getPaths().forEach((path, pathItem) ->
                pathItem.readOperationsMap().forEach((method, operation) ->
                        endpoints.add(toEndpoint(path, method, pathItem, operation, openApi))
                )
        );
        return List.copyOf(endpoints);
    }

    private ParsedEndpoint toEndpoint(
            String path,
            PathItem.HttpMethod method,
            PathItem pathItem,
            Operation operation,
            OpenAPI openApi
    ) {
        List<Parameter> parameters = new ArrayList<>();
        if (pathItem.getParameters() != null) {
            parameters.addAll(pathItem.getParameters());
        }
        if (operation.getParameters() != null) {
            parameters.addAll(operation.getParameters());
        }
        List<ParsedParameter> parsedParameters = parameters.stream()
                .map(parameter -> toParameter(parameter, openApi))
                .toList();

        return new ParsedEndpoint(
                path,
                method.name(),
                operation.getOperationId(),
                operation.getSummary(),
                operation.getDescription(),
                toJson(operation.getTags()),
                Boolean.TRUE.equals(operation.getDeprecated()),
                toJson(operation.getRequestBody()),
                toJson(operation.getResponses()),
                toJson(operation.getSecurity()),
                parsedParameters
        );
    }

    private ParsedParameter toParameter(Parameter parameter, OpenAPI openApi) {
        Parameter effective = parameter;
        if (parameter.get$ref() != null && parameter.get$ref().startsWith("#/components/parameters/")
                && openApi.getComponents() != null && openApi.getComponents().getParameters() != null) {
            String name = parameter.get$ref().substring("#/components/parameters/".length());
            effective = openApi.getComponents().getParameters().getOrDefault(name, parameter);
        }
        return new ParsedParameter(
                effective.getName() == null ? "unnamed" : effective.getName(),
                effective.getIn() == null ? "unknown" : effective.getIn(),
                Boolean.TRUE.equals(effective.getRequired()),
                effective.getDescription(),
                toJson(effective.getSchema())
        );
    }

    private List<ParsedSchema> parseSchemas(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return List.of();
        }
        return openApi.getComponents().getSchemas().entrySet().stream()
                .map(entry -> new ParsedSchema(entry.getKey(), toJson(entry.getValue())))
                .toList();
    }

    private List<ParsedSecurityScheme> parseSecuritySchemes(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSecuritySchemes() == null) {
            return List.of();
        }
        return openApi.getComponents().getSecuritySchemes().entrySet().stream()
                .map(this::toSecurityScheme)
                .toList();
    }

    private ParsedSecurityScheme toSecurityScheme(Map.Entry<String, SecurityScheme> entry) {
        SecurityScheme scheme = entry.getValue();
        return new ParsedSecurityScheme(
                entry.getKey(),
                scheme.getType() == null ? "unknown" : scheme.getType().name(),
                scheme.getScheme(),
                scheme.getBearerFormat(),
                scheme.getName(),
                scheme.getIn() == null ? null : scheme.getIn().name(),
                scheme.getOpenIdConnectUrl(),
                toJson(scheme.getFlows())
        );
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Json.mapper().writeValueAsString(value);
        } catch (Exception exception) {
            throw new OpenApiParseException("OpenAPI 节点序列化失败", exception);
        }
    }

    private String formatMessages(List<String> messages) {
        if (messages == null || messages.isEmpty()) {
            return "文档未包含任何接口路径";
        }
        List<String> safeMessages = new ArrayList<>(messages);
        Collections.sort(safeMessages);
        return String.join("; ", safeMessages);
    }
}
