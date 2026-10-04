package com.dochelper.maintenance.application;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.dochelper.common.exception.BusinessException;
import com.dochelper.common.exception.CommonErrorCode;
import com.dochelper.maintenance.api.dto.CleanupRequest;
import com.dochelper.maintenance.api.vo.CleanupPreviewResponse;
import com.dochelper.maintenance.domain.CleanupScope;
import com.dochelper.maintenance.infrastructure.ProjectDataRepository;
import com.dochelper.project.domain.repository.ApiProjectRepository;
import com.dochelper.project.exception.ProjectErrorCode;
import com.dochelper.secret.application.SecretStore;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 清理先预览、再核对同一范围，事务失败时保留全部业务记录。 */
@Service
public class ProjectDataService {
    private final ProjectDataRepository data;
    private final ApiProjectRepository projects;
    private final SecretStore secrets;
    private final TransactionTemplate transactions;
    private final Map<Long, Preview> previews = new LinkedHashMap<>();
    public ProjectDataService(ProjectDataRepository data, ApiProjectRepository projects, SecretStore secrets,
            TransactionTemplate transactions) {
        this.data = data; this.projects = projects; this.secrets = secrets; this.transactions = transactions;
    }
    public Map<String, Long> counts(Long project) { requireProject(project); return data.counts(project); }
    public List<Map<String, Object>> recycled() { return data.recycled(); }

    public boolean restore(Long project) {
        try {
            boolean restored = Boolean.TRUE.equals(transactions.execute(status -> data.restore(project)));
            if (!restored) throw new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND);
            return true;
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ProjectErrorCode.PROJECT_CODE_CONFLICT,
                    "项目编码已被另一个项目使用，请先处理重复编码");
        }
    }

    public synchronized CleanupPreviewResponse preview(Long project, int retentionDays) {
        requireProject(project);
        if (retentionDays < 7 || retentionDays > 3650) throw invalid("保留时间须为 7—3650 天");
        LocalDateTime now = LocalDateTime.now();
        previews.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        if (previews.size() >= 100 && !previews.containsKey(project)) throw invalid("待确认预览过多，请稍后再试");
        CleanupScope scope = data.scope(project, now.minusDays(retentionDays));
        Preview preview = new Preview(UUID.randomUUID().toString(), now.plusMinutes(5), scope);
        previews.put(project, preview);
        return new CleanupPreviewResponse(preview.id(), scope.before(), preview.expiresAt(), scope.tasks().size(),
                scope.reports().size(), scope.executions().size(),
                "只清理所选项目的旧终态历史及关联审计；保留待核验任务、接口资料、环境、业务文档和当前模型配置。每批最多 500 个任务和执行。");
    }

    public synchronized Map<String, Integer> clean(Long project, CleanupRequest request) {
        var current = requireProject(project);
        Preview preview = previews.get(project);
        if (preview == null || !preview.id().equals(request.previewId()) || !preview.expiresAt().isAfter(LocalDateTime.now()))
            throw invalid("预览已失效，请重新查看清理范围");
        if (!current.code().equals(request.projectCode())) throw invalid("请输入当前项目的完整编码确认清理");
        Map<String, Integer> result = transactions.execute(status -> {
            projects.lockById(project);
            CleanupScope fresh = data.scope(project, preview.scope().before());
            if (!fresh.equals(preview.scope())) throw invalid("历史记录已变化，请重新预览后再清理");
            data.deleteHistory(project, fresh).forEach(secrets::delete);
            return Map.of("tasks", fresh.tasks().size(), "reports", fresh.reports().size(), "executions", fresh.executions().size());
        });
        previews.remove(project);
        return result;
    }
    private com.dochelper.project.domain.ApiProject requireProject(Long project) {
        return projects.findById(project).orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));
    }
    private static BusinessException invalid(String message) { return new BusinessException(CommonErrorCode.INVALID_ARGUMENT, message); }
    private record Preview(String id, LocalDateTime expiresAt, CleanupScope scope) { }
}
