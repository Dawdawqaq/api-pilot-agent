package com.dochelper.governance.application;

import com.dochelper.agent.config.AgentProperties;
import com.dochelper.governance.api.dto.UpdateModelPolicyRequest;
import com.dochelper.governance.domain.ProjectModelPolicy;
import com.dochelper.governance.domain.repository.ProjectModelPolicyRepository;
import org.springframework.stereotype.Service;

/**
 * 项目模型数据策略管理服务。
 */
@Service
public class ProjectModelPolicyService {

    private final ProjectModelPolicyRepository repository;
    private final AgentProperties agentProperties;

    public ProjectModelPolicyService(
            ProjectModelPolicyRepository repository,
            AgentProperties agentProperties
    ) {
        this.repository = repository;
        this.agentProperties = agentProperties;
    }

    public ProjectModelPolicy get(Long projectId) {
        return repository.findByProjectId(projectId)
                .orElseGet(() -> ProjectModelPolicy.defaults(
                        projectId, agentProperties.defaultEndpointTopK()
                ));
    }

    public ProjectModelPolicy update(Long projectId, UpdateModelPolicyRequest request) {
        return repository.save(new ProjectModelPolicy(
                projectId, request.externalModelAllowed(), request.allowedProvider().trim(),
                request.allowDocumentContent(), request.allowSchemaContent(),
                request.promptCharacterBudget(), request.endpointTopK()
        ));
    }
}
