CREATE INDEX idx_replay_project_id ON failure_replay_sample (project_id, id);
UPDATE sys_setting SET config_value = '19', description = 'DocHelper 数据库结构版本'
WHERE config_key = 'application.schema.version';
