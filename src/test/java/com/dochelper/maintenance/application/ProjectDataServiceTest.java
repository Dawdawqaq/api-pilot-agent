package com.dochelper.maintenance.application;

import java.util.List;
import java.util.Optional;
import com.dochelper.maintenance.api.dto.CleanupRequest;
import com.dochelper.maintenance.domain.CleanupScope;
import com.dochelper.maintenance.infrastructure.ProjectDataRepository;
import com.dochelper.project.domain.ApiProject;
import com.dochelper.project.domain.repository.ApiProjectRepository;
import com.dochelper.secret.application.SecretStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 二次确认绑定项目和最新范围，不允许凭过期或跨项目令牌清理。 */
class ProjectDataServiceTest {
    private final ProjectDataRepository data = mock(ProjectDataRepository.class);
    private final ApiProjectRepository projects = mock(ApiProjectRepository.class);
    private final SecretStore secrets = mock(SecretStore.class);
    private ProjectDataService service;
    @BeforeEach void prepare() {
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var project = mock(ApiProject.class); when(project.code()).thenReturn("own-project");
        when(projects.findById(anyLong())).thenReturn(Optional.of(project));
        when(data.scope(anyLong(),any())).thenAnswer(invocation -> new CleanupScope(invocation.getArgument(1),List.of(11L),List.of(12L),List.of(13L)));
        service = new ProjectDataService(data,projects,secrets,new TransactionTemplate(manager));
    }
    @Test void shouldRejectWrongProjectOrCodeWithoutDeletingAnything() {
        var preview = service.preview(1L,30);
        assertThatThrownBy(() -> service.clean(2L,new CleanupRequest(preview.previewId(),"own-project"))).hasMessageContaining("预览已失效");
        assertThatThrownBy(() -> service.clean(1L,new CleanupRequest(preview.previewId(),"other"))).hasMessageContaining("完整编码");
        verify(data,never()).deleteHistory(anyLong(),any()); verifyNoInteractions(secrets);
    }
    @Test void shouldRejectScopeChangeAfterPreview() {
        var preview = service.preview(1L,30);
        when(data.scope(eq(1L),any())).thenReturn(new CleanupScope(preview.before(),List.of(11L,14L),List.of(12L),List.of(13L)));
        assertThatThrownBy(() -> service.clean(1L,new CleanupRequest(preview.previewId(),"own-project"))).hasMessageContaining("记录已变化");
        verify(data,never()).deleteHistory(anyLong(),any());
    }
    @Test void shouldDeleteOnlyConfirmedScopeAndItsSecretReferencesAndConsumeToken() {
        var preview = service.preview(1L,30);
        when(data.deleteHistory(eq(1L),any())).thenReturn(List.of("db:old-runtime"));
        assertThat(service.clean(1L,new CleanupRequest(preview.previewId(),"own-project"))).containsEntry("tasks",1);
        verify(projects).lockById(1L); verify(secrets).delete("db:old-runtime");
        assertThatThrownBy(() -> service.clean(1L,new CleanupRequest(preview.previewId(),"own-project"))).hasMessageContaining("预览已失效");
    }
    @Test void shouldRejectUnsafeRetentionAndReplaceOldPreview() {
        assertThatThrownBy(() -> service.preview(1L,0)).hasMessageContaining("7—3650");
        var first = service.preview(1L,30); service.preview(1L,7);
        assertThatThrownBy(() -> service.clean(1L,new CleanupRequest(first.previewId(),"own-project"))).hasMessageContaining("预览已失效");
    }
}
