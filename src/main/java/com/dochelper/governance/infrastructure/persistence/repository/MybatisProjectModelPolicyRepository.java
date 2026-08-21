package com.dochelper.governance.infrastructure.persistence.repository;

import java.util.Optional;

import com.dochelper.governance.domain.ProjectModelPolicy;
import com.dochelper.governance.domain.repository.ProjectModelPolicyRepository;
import com.dochelper.governance.infrastructure.persistence.entity.ProjectModelPolicyEntity;
import com.dochelper.governance.infrastructure.persistence.mapper.ProjectModelPolicyMapper;
import org.springframework.stereotype.Repository;

/**
 * 项目模型策略 MyBatis 仓储。
 */
@Repository
public class MybatisProjectModelPolicyRepository implements ProjectModelPolicyRepository {

    private final ProjectModelPolicyMapper mapper;

    public MybatisProjectModelPolicyRepository(ProjectModelPolicyMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<ProjectModelPolicy> findByProjectId(Long projectId) {
        return Optional.ofNullable(mapper.selectById(projectId)).map(this::toDomain);
    }

    @Override
    public ProjectModelPolicy save(ProjectModelPolicy policy) {
        ProjectModelPolicyEntity entity = new ProjectModelPolicyEntity();
        entity.setProjectId(policy.projectId());
        entity.setExternalModelAllowed(policy.externalModelAllowed());
        entity.setAllowedProvider(policy.allowedProvider());
        entity.setAllowDocumentContent(policy.allowDocumentContent());
        entity.setAllowSchemaContent(policy.allowSchemaContent());
        entity.setPromptCharacterBudget(policy.promptCharacterBudget());
        entity.setEndpointTopK(policy.endpointTopK());
        if (mapper.selectById(policy.projectId()) == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
        return findByProjectId(policy.projectId()).orElseThrow();
    }

    private ProjectModelPolicy toDomain(ProjectModelPolicyEntity entity) {
        return new ProjectModelPolicy(
                entity.getProjectId(), Boolean.TRUE.equals(entity.getExternalModelAllowed()),
                entity.getAllowedProvider(), Boolean.TRUE.equals(entity.getAllowDocumentContent()),
                Boolean.TRUE.equals(entity.getAllowSchemaContent()), entity.getPromptCharacterBudget(),
                entity.getEndpointTopK()
        );
    }
}
