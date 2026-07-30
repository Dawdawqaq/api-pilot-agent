CREATE TABLE agent_model_call
(
    id                BIGINT UNSIGNED NOT NULL,
    task_id           BIGINT UNSIGNED NOT NULL,
    model_name        VARCHAR(128)    NOT NULL,
    status            VARCHAR(24)     NOT NULL,
    attempt           INT UNSIGNED    NOT NULL,
    prompt_tokens     INT UNSIGNED    NOT NULL DEFAULT 0,
    completion_tokens INT UNSIGNED    NOT NULL DEFAULT 0,
    total_tokens      INT UNSIGNED    NOT NULL DEFAULT 0,
    duration_ms       BIGINT UNSIGNED NOT NULL,
    error_code        VARCHAR(64)     NULL,
    error_message     VARCHAR(500)    NULL,
    created_at        DATETIME(3)     NOT NULL,
    completed_at      DATETIME(3)     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_agent_model_call_task (task_id, created_at),
    CONSTRAINT fk_agent_model_call_task
        FOREIGN KEY (task_id) REFERENCES agent_task (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Agent 模型调用指标审计';

UPDATE sys_setting
SET config_value = '7',
    description = 'DocHelper 数据库结构版本'
WHERE config_key = 'application.schema.version';
