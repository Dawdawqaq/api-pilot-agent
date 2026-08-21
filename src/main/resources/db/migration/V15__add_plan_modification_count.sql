ALTER TABLE agent_task
    ADD COLUMN modification_count INT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '人工多轮修改计划次数'
        AFTER replan_count;
