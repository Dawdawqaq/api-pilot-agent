CREATE TABLE environment_endpoint_policy
(
    id             BIGINT UNSIGNED NOT NULL,
    environment_id BIGINT UNSIGNED NOT NULL,
    http_method    VARCHAR(10)     NOT NULL,
    path_template  VARCHAR(700)    NOT NULL,
    risk_override  VARCHAR(24)     NULL,
    enabled        TINYINT(1)      NOT NULL DEFAULT 1,
    created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_environment_endpoint_policy (environment_id, http_method, path_template),
    KEY idx_environment_endpoint_enabled (environment_id, enabled),
    CONSTRAINT fk_environment_endpoint_policy_environment
        FOREIGN KEY (environment_id) REFERENCES api_environment (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '环境级接口风险覆盖策略';

ALTER TABLE agent_confirmation
    ADD COLUMN decided_by_user_id BIGINT UNSIGNED NULL AFTER decision_note,
    ADD COLUMN plan_hash CHAR(64) NULL AFTER request_json,
    ADD KEY idx_agent_confirmation_decided_user (decided_by_user_id),
    ADD CONSTRAINT fk_agent_confirmation_decided_user
        FOREIGN KEY (decided_by_user_id) REFERENCES app_user (id);

UPDATE sys_setting
SET config_value = '9',
    description = 'DocHelper 数据库结构版本'
WHERE config_key = 'application.schema.version';
