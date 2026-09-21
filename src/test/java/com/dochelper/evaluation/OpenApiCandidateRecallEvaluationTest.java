package com.dochelper.evaluation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.dochelper.agent.application.OpenApiCandidateSelector;
import com.dochelper.openapi.application.OpenApiDocumentParser;
import com.dochelper.openapi.domain.ApiEndpoint;
import com.dochelper.openapi.domain.ParsedEndpoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用固定的三类 OpenAPI 和五十条中文目标计算候选接口召回指标。
 */
class OpenApiCandidateRecallEvaluationTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final OpenApiCandidateSelector selector = new OpenApiCandidateSelector();

    @Test
    void shouldMeetCandidateRecallBaseline() throws Exception {
        Map<String, List<ApiEndpoint>> catalogs = new LinkedHashMap<>();
        OpenApiDocumentParser parser = new OpenApiDocumentParser();
        for (String service : List.of("small", "medium", "large")) {
            List<ParsedEndpoint> parsed = parser.parse(resource(
                    "evaluation/services/" + service + ".yaml"
            )).endpoints();
            catalogs.put(service, toEndpoints(parsed));
        }

        JsonNode cases = objectMapper.readTree(resource("evaluation/api-pilot-evaluation-v1.json"));
        List<Map<String, Object>> details = new ArrayList<>();
        int expectedOperationCount = 0;
        int recalledAt3 = 0;
        int recalledAt12 = 0;
        int completeAt12 = 0;
        int singleCount = 0;
        int top1 = 0;
        double reciprocalRankSum = 0;

        for (JsonNode evaluationCase : cases) {
            String service = evaluationCase.path("service").asText();
            String goal = evaluationCase.path("goal").asText();
            List<String> expected = Arrays.stream(
                            evaluationCase.path("expectedOperation").asText().split(",")
                    ).map(String::trim).toList();
            List<OpenApiCandidateSelector.RankedEndpoint> ranked = selector.select(
                    goal, catalogs.get(service), 12
            );
            List<String> actual = ranked.stream()
                    .map(candidate -> candidate.endpoint().operationId())
                    .toList();
            expectedOperationCount += expected.size();
            recalledAt3 += (int) expected.stream().filter(actual.stream().limit(3).toList()::contains).count();
            recalledAt12 += (int) expected.stream().filter(actual::contains).count();
            if (actual.containsAll(expected)) {
                completeAt12++;
            }
            if (expected.size() == 1) {
                singleCount++;
                if (!actual.isEmpty() && expected.getFirst().equals(actual.getFirst())) {
                    top1++;
                }
                int index = actual.indexOf(expected.getFirst());
                reciprocalRankSum += index < 0 ? 0 : 1.0 / (index + 1);
            }
            details.add(Map.of(
                    "id", evaluationCase.path("id").asText(),
                    "goal", goal,
                    "expected", expected,
                    "actual", actual,
                    "completeAt12", actual.containsAll(expected)
            ));
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("datasetVersion", "api-pilot-evaluation-v1");
        metrics.put("caseCount", cases.size());
        metrics.put("serviceCount", catalogs.size());
        metrics.put("expectedOperationCount", expectedOperationCount);
        metrics.put("singleOperationCaseCount", singleCount);
        metrics.put("top1Accuracy", ratio(top1, singleCount));
        metrics.put("mrr", ratio(reciprocalRankSum, singleCount));
        metrics.put("operationRecallAt3", ratio(recalledAt3, expectedOperationCount));
        metrics.put("operationRecallAt12", ratio(recalledAt12, expectedOperationCount));
        metrics.put("caseCompleteRecallAt12", ratio(completeAt12, cases.size()));
        metrics.put("details", details);

        Path output = Path.of("target", "evaluation", "candidate-recall.json");
        Files.createDirectories(output.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), metrics);
        Map<String, Object> conciseMetrics = new LinkedHashMap<>(metrics);
        conciseMetrics.remove("details");
        System.out.println("CANDIDATE_RECALL_METRICS=" + objectMapper.writeValueAsString(conciseMetrics));

        assertThat(cases.size()).isEqualTo(50);
        assertThat(catalogs.values().stream().mapToInt(List::size).sum()).isEqualTo(36);
        assertThat(ratio(top1, singleCount)).isGreaterThanOrEqualTo(0.95);
        assertThat(ratio(recalledAt3, expectedOperationCount)).isGreaterThanOrEqualTo(0.90);
        assertThat(ratio(recalledAt12, expectedOperationCount)).isEqualTo(1.0);
        assertThat(ratio(completeAt12, cases.size())).isEqualTo(1.0);
    }

    private List<ApiEndpoint> toEndpoints(List<ParsedEndpoint> parsed) {
        java.util.concurrent.atomic.AtomicLong ids = new java.util.concurrent.atomic.AtomicLong(1);
        return parsed.stream().map(endpoint -> new ApiEndpoint(
                ids.getAndIncrement(), 1L, 1L, endpoint.path(), endpoint.httpMethod(),
                endpoint.operationId(), endpoint.summary(), endpoint.description(), endpoint.tagsJson(),
                endpoint.deprecated(), endpoint.requestBodyJson(), endpoint.responsesJson(),
                endpoint.securityJson(), endpoint.parameters()
        )).toList();
    }

    private double ratio(double numerator, int denominator) {
        return denominator == 0 ? 0 : Math.round(numerator / denominator * 10_000.0) / 10_000.0;
    }

    private String resource(String path) throws Exception {
        return new String(new ClassPathResource(path).getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
