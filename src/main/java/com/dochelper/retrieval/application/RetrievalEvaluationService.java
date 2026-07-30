package com.dochelper.retrieval.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dochelper.common.exception.BusinessException;
import com.dochelper.knowledge.domain.DocumentStatus;
import com.dochelper.knowledge.domain.repository.KnowledgeRepository;
import com.dochelper.knowledge.exception.KnowledgeErrorCode;
import com.dochelper.project.domain.repository.ApiProjectRepository;
import com.dochelper.project.exception.ProjectErrorCode;
import com.dochelper.retrieval.domain.EvaluationCase;
import com.dochelper.retrieval.domain.EvaluationRun;
import com.dochelper.retrieval.domain.RetrievalResult;
import com.dochelper.retrieval.domain.repository.EvaluationRepository;
import org.springframework.stereotype.Service;

/**
 * 检索评测用例和 Recall@K 计算服务。
 */
@Service
public class RetrievalEvaluationService {

    private final EvaluationRepository repository;
    private final KnowledgeRepository knowledgeRepository;
    private final ApiProjectRepository projectRepository;
    private final HybridRetrievalService retrievalService;
    private final ObjectMapper objectMapper;

    public RetrievalEvaluationService(
            EvaluationRepository repository,
            KnowledgeRepository knowledgeRepository,
            ApiProjectRepository projectRepository,
            HybridRetrievalService retrievalService,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.knowledgeRepository = knowledgeRepository;
        this.projectRepository = projectRepository;
        this.retrievalService = retrievalService;
        this.objectMapper = objectMapper;
    }

    public EvaluationCase createCase(
            Long projectId,
            String name,
            String query,
            Long expectedDocumentId
    ) {
        requireProject(projectId);
        repository.findCaseByName(projectId, name.trim()).ifPresent(existing -> {
            throw new BusinessException(KnowledgeErrorCode.EVALUATION_CASE_CONFLICT);
        });
        var document = knowledgeRepository.findDocument(projectId, expectedDocumentId)
                .orElseThrow(() -> new BusinessException(KnowledgeErrorCode.DOCUMENT_NOT_FOUND));
        if (document.status() != DocumentStatus.INDEXED) {
            throw new BusinessException(
                    KnowledgeErrorCode.DOCUMENT_NOT_INDEXED,
                    "评测目标文档尚未完成索引"
            );
        }
        return repository.createCase(new EvaluationCase(
                IdWorker.getId(),
                projectId,
                name.trim(),
                query.trim(),
                expectedDocumentId,
                null
        ));
    }

    public List<EvaluationCase> listCases(Long projectId) {
        requireProject(projectId);
        return repository.findCases(projectId);
    }

    public EvaluationRun run(Long projectId, int topK) {
        requireProject(projectId);
        int safeTopK = Math.max(1, Math.min(topK, 20));
        List<EvaluationCase> cases = repository.findCases(projectId);
        if (cases.isEmpty()) {
            throw new BusinessException(KnowledgeErrorCode.NO_EVALUATION_CASES);
        }
        int hitCount = 0;
        List<Map<String, Object>> details = new ArrayList<>();
        for (EvaluationCase evaluationCase : cases) {
            List<RetrievalResult> results = retrievalService.search(
                    projectId,
                    evaluationCase.query(),
                    safeTopK
            );
            boolean hit = results.stream().anyMatch(result ->
                    result.documentId().equals(evaluationCase.expectedDocumentId())
            );
            if (hit) {
                hitCount++;
            }
            details.add(Map.of(
                    "caseId", evaluationCase.id(),
                    "caseName", evaluationCase.name(),
                    "query", evaluationCase.query(),
                    "expectedDocumentId", evaluationCase.expectedDocumentId(),
                    "hit", hit,
                    "retrievedDocumentIds", results.stream()
                            .map(RetrievalResult::documentId)
                            .distinct()
                            .toList()
            ));
        }
        double recall = (double) hitCount / cases.size();
        return repository.createRun(new EvaluationRun(
                IdWorker.getId(),
                projectId,
                safeTopK,
                cases.size(),
                hitCount,
                recall,
                toJson(details),
                null
        ));
    }

    public List<EvaluationRun> listRuns(Long projectId) {
        requireProject(projectId);
        return repository.findRuns(projectId);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("检索评测详情序列化失败", exception);
        }
    }

    private void requireProject(Long projectId) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));
    }
}
