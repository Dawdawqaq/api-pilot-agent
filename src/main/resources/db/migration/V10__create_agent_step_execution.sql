CREATE TABLE agent_step_execution
(
    id                        BIGINT UNSIGNED NOT NULL,
    task_id                   BIGINT UNSIGNED NOT NULL,
    execution_id              BIGINT UNSIGNED NOT NULL,
    step_index                INT UNSIGNED    NOT NULL,
    attempt                   INT UNSIGNED    NOT NULL,
    status                    VARCHAR(24)     NOT NULL,
    request_fingerprint       CHAR(64)        NOT NULL,
    idempotency_key_hash      CHAR(64)        NULL,
    input_variable_names_json JSON            NOT NULL,
    output_secret_ref         VARCHAR(128)    NULL,
    response_status           INT             NULL,
    error_category            VARCHAR(32)     NULL,
    error_code                VARCHAR(64)     NULL,
    error_message             VARCHAR(1000)   NULL,
    duration_ms               BIGINT UNSIGNED NULL,
    created_at                DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    completed_at              DATETIME(3)     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_step_attempt (task_id, step_index, attempt),
    KEY idx_agent_step_fingerprint (task_id, request_fingerprint, status),
    KEY idx_agent_step_idempotency (idempotency_key_hash),
    KEY idx_agent_step_execution (execution_id, step_index),
    CONSTRAINT fk_agent_step_task FOREIGN KEY (task_id) REFERENCES agent_task (id),
    CONSTRAINT fk_agent_step_execution FOREIGN KEY (execution_id) REFERENCES api_execution (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Agent 步骤级执行、恢复与幂等审计';

UPDATE sys_setting
SET config_value = '10',
    description = 'DocHelper 数据库结构版本'
WHERE config_key = 'application.schema.version';
