ALTER TABLE agent_confirmation
    DROP INDEX uk_agent_confirmation_task_step,
    ADD UNIQUE KEY uk_agent_confirmation_plan (task_id, step_index, plan_hash);

UPDATE sys_setting
SET config_value = '12',
    description = 'DocHelper 数据库结构版本'
WHERE config_key = 'application.schema.version';
