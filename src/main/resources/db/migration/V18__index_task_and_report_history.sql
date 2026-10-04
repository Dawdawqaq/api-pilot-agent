-- 为项目内按主键游标读取历史提供索引，避免依赖不稳定的页偏移。
CREATE INDEX idx_agent_task_project_id ON agent_task (project_id, id);
CREATE INDEX idx_test_report_project_id ON test_report (project_id, id);
