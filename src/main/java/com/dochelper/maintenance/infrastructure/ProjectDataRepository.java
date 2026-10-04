package com.dochelper.maintenance.infrastructure;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.dochelper.maintenance.domain.CleanupScope;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 维护查询集中在仓储层，表名和删除顺序由固定白名单控制。 */
@Repository
public class ProjectDataRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public ProjectDataRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, Long> counts(Long projectId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : List.of("agent_task", "test_report", "api_execution", "failure_replay_sample",
                "openapi_import", "knowledge_document", "knowledge_chunk")) {
            counts.put(table, jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE project_id=:project",
                    Map.of("project", projectId), Long.class));
        }
        counts.put("documentBytes", jdbc.queryForObject("SELECT COALESCE(SUM(file_size),0) FROM knowledge_document"
                + " WHERE project_id=:project AND deleted=0", Map.of("project", projectId), Long.class));
        counts.put("activeTasks", jdbc.queryForObject("SELECT COUNT(*) FROM agent_task WHERE project_id=:project"
                + " AND status NOT IN ('SUCCEEDED','FAILED','CANCELLED','NEEDS_REVIEW')", Map.of("project", projectId), Long.class));
        return counts;
    }

    public CleanupScope scope(Long project, LocalDateTime before) {
        Map<String, Object> params = new LinkedHashMap<>(Map.of("project", project, "before", before));
        List<Long> tasks = ids("SELECT id FROM agent_task WHERE project_id=:project AND completed_at<:before"
                + " AND status IN ('SUCCEEDED','FAILED','CANCELLED') AND (lease_until IS NULL OR lease_until<NOW(3))"
                + " AND NOT EXISTS (SELECT 1 FROM test_report r WHERE r.task_id=agent_task.id AND r.created_at>=:before)"
                + " ORDER BY id LIMIT 500", params);
        params.put("tasks", tasks.isEmpty() ? List.of(-1L) : tasks);
        List<Long> executions = ids("SELECT e.id FROM api_execution e WHERE e.project_id=:project"
                + " AND e.completed_at<:before AND e.status IN ('SUCCEEDED','FAILED','CANCELLED')"
                + " AND NOT EXISTS (SELECT 1 FROM agent_step_execution s WHERE s.execution_id=e.id AND s.task_id NOT IN (:tasks))"
                + " AND NOT EXISTS (SELECT 1 FROM test_report r WHERE r.execution_id=e.id AND r.task_id IS NOT NULL AND r.task_id NOT IN (:tasks))"
                + " ORDER BY e.id LIMIT 500", params);
        params.put("executions", executions.isEmpty() ? List.of(-1L) : executions);
        List<Long> reports = ids("SELECT id FROM test_report WHERE project_id=:project AND created_at<:before"
                + " AND (task_id IN (:tasks) OR (task_id IS NULL AND execution_id IN (:executions))) ORDER BY id", params);
        // 有较新报告仍引用的执行不能删除，避免释放一段历史却损坏另一份证据。
        params.put("reports", reports.isEmpty() ? List.of(-1L) : reports);
        executions = executions.stream().filter(id -> jdbc.queryForObject("SELECT COUNT(*) FROM test_report r"
                + " WHERE r.id NOT IN (:reports) AND (r.execution_id=:execution OR EXISTS"
                + " (SELECT 1 FROM test_report_step rs JOIN api_execution_step es ON es.id=rs.execution_step_id"
                + " WHERE rs.report_id=r.id AND es.execution_id=:execution))",
                Map.of("execution", id, "reports", params.get("reports")), Long.class) == 0).toList();
        return new CleanupScope(before, List.copyOf(tasks), List.copyOf(reports), List.copyOf(executions));
    }

    public List<String> deleteHistory(Long project, CleanupScope scope) {
        var params = new LinkedHashMap<String, Object>();
        params.put("project", project);
        params.put("tasks", scope.tasks().isEmpty() ? List.of(-1L) : scope.tasks());
        params.put("reports", scope.reports().isEmpty() ? List.of(-1L) : scope.reports());
        params.put("executions", scope.executions().isEmpty() ? List.of(-1L) : scope.executions());
        var references = new java.util.ArrayList<String>();
        references.addAll(jdbc.queryForList("SELECT output_secret_ref FROM agent_step_execution"
                + " WHERE task_id IN (:tasks) AND output_secret_ref IS NOT NULL", params, String.class));
        references.addAll(jdbc.queryForList("SELECT request_secret_ref FROM failure_replay_sample"
                + " WHERE project_id=:project AND execution_id IN (:executions)", params, String.class));
        references.addAll(jdbc.queryForList("SELECT JSON_UNQUOTE(JSON_EXTRACT(context_json_redacted,'$.runtimeContextRef'))"
                + " FROM agent_task WHERE project_id=:project AND id IN (:tasks)", params, String.class)
                .stream().filter(value -> value != null && !"null".equals(value)).toList());
        jdbc.update("DELETE FROM test_run_coverage WHERE project_id=:project AND report_id IN (:reports)", params);
        jdbc.update("DELETE FROM test_report_step WHERE report_id IN (:reports)", params);
        jdbc.update("DELETE FROM test_report WHERE project_id=:project AND id IN (:reports)", params);
        for (String table : List.of("agent_step_execution", "agent_confirmation", "agent_model_call",
                "agent_tool_call", "agent_task_event", "agent_message")) {
            jdbc.update("DELETE FROM " + table + " WHERE task_id IN (:tasks)", params);
        }
        jdbc.update("DELETE FROM agent_task WHERE project_id=:project AND id IN (:tasks)", params);
        jdbc.update("DELETE FROM agent_conversation WHERE project_id=:project"
                + " AND NOT EXISTS (SELECT 1 FROM agent_task t WHERE t.conversation_id=agent_conversation.id)"
                + " AND NOT EXISTS (SELECT 1 FROM agent_message m WHERE m.conversation_id=agent_conversation.id)", params);
        for (String table : List.of("failure_replay_sample", "contract_operation_result", "test_run_coverage")) {
            jdbc.update("DELETE FROM " + table + " WHERE project_id=:project AND execution_id IN (:executions)", params);
        }
        jdbc.update("DELETE FROM api_execution_step WHERE execution_id IN (:executions)", params);
        jdbc.update("DELETE FROM api_execution WHERE project_id=:project AND id IN (:executions)", params);
        return references;
    }

    public List<Map<String, Object>> recycled() {
        return jdbc.queryForList("SELECT CAST(id AS CHAR) AS id, project_code AS code, project_name AS name"
                + " FROM api_project WHERE deleted=1 ORDER BY updated_at DESC", Map.of());
    }

    public boolean restore(Long project) {
        return jdbc.update("UPDATE api_project SET deleted=0 WHERE id=:project AND deleted=1", Map.of("project", project)) == 1;
    }

    private List<Long> ids(String sql, Map<String, ?> params) { return jdbc.queryForList(sql, params, Long.class); }
}
