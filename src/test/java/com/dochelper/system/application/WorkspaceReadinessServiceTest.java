package com.dochelper.system.application;

import java.util.List;
import java.util.Optional;
import com.dochelper.executor.application.TargetAccessPolicy;
import com.dochelper.governance.application.ProjectModelPolicyService;
import com.dochelper.governance.domain.ProjectModelPolicy;
import com.dochelper.model.application.LlmConfigurationService;
import com.dochelper.model.api.vo.LlmConfigurationResponse;
import com.dochelper.openapi.domain.ApiEndpoint;
import com.dochelper.openapi.domain.repository.OpenApiCatalogRepository;
import com.dochelper.project.domain.*;
import com.dochelper.project.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 配置就绪不虚构联网成功，知识库缺失也不会阻止核心测试。 */
class WorkspaceReadinessServiceTest {
    private final ApiProjectRepository projects = mock(ApiProjectRepository.class);
    private final ProjectEnvironmentRepository environments = mock(ProjectEnvironmentRepository.class);
    private final OpenApiCatalogRepository catalog = mock(OpenApiCatalogRepository.class);
    private final LlmConfigurationService models = mock(LlmConfigurationService.class);
    private final ProjectModelPolicyService policies = mock(ProjectModelPolicyService.class);
    private WorkspaceReadinessService service;
    @BeforeEach void prepare() {
        when(projects.findById(1L)).thenReturn(Optional.of(new ApiProject(1L,"test","测试",null,ProjectStatus.ACTIVE,2L,null,null)));
        when(catalog.findEndpoints(1L,2L)).thenReturn(List.of(mock(ApiEndpoint.class)));
        when(models.getConfiguration()).thenReturn(new LlmConfigurationResponse("API","DEEPSEEK","https://api.deepseek.com","model","model",true,"SAVED",true));
        when(policies.get(1L)).thenReturn(ProjectModelPolicy.defaults(1L,5));
        when(environments.findByIdAndProjectId(3L,1L)).thenReturn(Optional.of(new ProjectEnvironment(3L,1L,"本地","http://127.0.0.1:18770","GET",true,true,null,null)));
        service = new WorkspaceReadinessService(projects,environments,catalog,models,policies,new TargetAccessPolicy());
    }
    @Test void shouldBeReadyWithoutKnowledgeAndExplicitlyMarkConnectionsUnverified() {
        var result = service.check(1L,3L);
        assertThat(result.ready()).isTrue();
        assertThat(result.checks()).anySatisfy(item -> { assertThat(item.key()).isEqualTo("knowledge"); assertThat(item.status()).isEqualTo("OPTIONAL"); });
        assertThat(result.checks()).anySatisfy(item -> assertThat(item.message()).contains("尚未探测"));
    }
    @Test void shouldBlockMissingCatalogEnvironmentAndExternalPolicy() {
        when(catalog.findEndpoints(1L,2L)).thenReturn(List.of());
        when(policies.get(1L)).thenReturn(new ProjectModelPolicy(1L,false,"ANY",true,true,20000,5));
        var result = service.check(1L,null);
        assertThat(result.ready()).isFalse();
        assertThat(result.checks().stream().filter(item -> "BLOCKED".equals(item.status())).map(item -> item.key()))
                .containsExactly("catalog","policy","environment");
    }
    @Test void shouldApplyRealPrivateNetworkPolicyBeforeCallingTargetReady() {
        when(environments.findByIdAndProjectId(3L,1L)).thenReturn(Optional.of(new ProjectEnvironment(3L,1L,"本地","http://127.0.0.1:18770","GET",false,true,null,null)));
        assertThat(service.check(1L,3L).ready()).isFalse();
    }
}
