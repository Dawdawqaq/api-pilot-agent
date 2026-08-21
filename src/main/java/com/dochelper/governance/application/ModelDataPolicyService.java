package com.dochelper.governance.application;

import java.util.List;

import com.dochelper.agent.config.AgentProperties;
import com.dochelper.agent.exception.AgentErrorCode;
import com.dochelper.common.exception.BusinessException;
import com.dochelper.governance.domain.ProjectModelPolicy;
import com.dochelper.governance.domain.repository.ProjectModelPolicyRepository;
import com.dochelper.openapi.domain.ApiEndpoint;
import com.dochelper.retrieval.domain.RetrievalResult;
import org.springframework.stereotype.Service;

/**
 * 执行项目级模型供应商、内容出站和 Prompt 预算策略。
 */
@Service
public class ModelDataPolicyService {

    private final ProjectModelPolicyRepository repository;
    private final AgentProperties agentProperties;

    public ModelDataPolicyService(
            ProjectModelPolicyRepository repository,
            AgentProperties agentProperties
    ) {
        this.repository = repository;
        this.agentProperties = agentProperties;
    }

    public ProjectModelPolicy requireAllowed(Long projectId, String provider) {
        ProjectModelPolicy policy = repository.findByProjectId(projectId)
                .orElseGet(() -> ProjectModelPolicy.defaults(
                        projectId, agentProperties.defaultEndpointTopK()
                ));
        if (!policy.externalModelAllowed()) {
            throw new BusinessException(AgentErrorCode.PLANNING_FAILED, "项目策略禁止调用外部模型");
        }
        if (!"ANY".equalsIgnoreCase(policy.allowedProvider())
                && !policy.allowedProvider().equalsIgnoreCase(provider)) {
            throw new BusinessException(AgentErrorCode.PLANNING_FAILED, "模型供应商不在项目允许范围内");
        }
        return policy;
    }

    public List<RetrievalResult> outboundEvidence(
            ProjectModelPolicy policy,
            List<RetrievalResult> evidence
    ) {
        if (policy.allowDocumentContent()) {
            return evidence;
        }
        return evidence.stream().map(item -> new RetrievalResult(
                item.chunkId(), item.documentId(), item.sourceName(), item.section(),
                item.chunkIndex(), "[内容出站已禁用]", item.fusedScore(),
                item.keywordRank(), item.vectorRank(), item.citation()
        )).toList();
    }

    public List<ApiEndpoint> outboundEndpoints(
            ProjectModelPolicy policy,
            List<ApiEndpoint> endpoints
    ) {
        List<ApiEndpoint> limited = endpoints.stream().limit(policy.endpointTopK()).toList();
        if (policy.allowSchemaContent()) {
            return limited;
        }
        return limited.stream().map(endpoint -> new ApiEndpoint(
                endpoint.id(), endpoint.projectId(), endpoint.importId(), endpoint.path(),
                endpoint.httpMethod(), endpoint.operationId(), endpoint.summary(), null,
                endpoint.tagsJson(), endpoint.deprecated(), null, null, null, List.of()
        )).toList();
    }
}
