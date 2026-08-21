package com.dochelper.evaluation;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.dochelper.openapi.application.OpenApiDocumentParser;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证最终评测资产规模和三种被测服务均可重复解析。
 */
class EvaluationAssetsTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void shouldProvideThreeServicesAndAtLeastFiftyCases() throws Exception {
        OpenApiDocumentParser parser = new OpenApiDocumentParser();
        List<Integer> operationCounts = List.of("small", "medium", "large").stream()
                .map(name -> parseCount(parser, "evaluation/services/" + name + ".yaml"))
                .toList();
        JsonNode cases = objectMapper.readTree(resource("evaluation/api-pilot-evaluation-v1.json"));
        JsonNode attacks = objectMapper.readTree(resource("evaluation/security-attacks-v1.json"));

        assertThat(operationCounts).containsExactly(2, 8, 26);
        assertThat(cases.size()).isGreaterThanOrEqualTo(50);
        assertThat(attacks.size()).isGreaterThanOrEqualTo(15);
        assertThat(java.util.stream.StreamSupport.stream(attacks.spliterator(), false)
                .map(node -> node.path("category").asText()).distinct().count()).isGreaterThanOrEqualTo(10);
    }

    private int parseCount(OpenApiDocumentParser parser, String path) {
        try {
            return parser.parse(resource(path)).endpoints().size();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String resource(String path) throws Exception {
        return new String(new ClassPathResource(path).getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
