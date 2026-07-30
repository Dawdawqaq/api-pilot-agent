ALTER TABLE api_environment
    ADD COLUMN allowed_methods VARCHAR(128) NOT NULL DEFAULT 'GET,POST' AFTER base_url,
    ADD COLUMN allow_private_network TINYINT(1) NOT NULL DEFAULT 0 AFTER allowed_methods;

CREATE TABLE api_execution
(
    id                     BIGINT UNSIGNED NOT NULL,
    project_id             BIGINT UNSIGNED NOT NULL,
    environment_id         BIGINT UNSIGNED NOT NULL,
    status                 VARCHAR(24)     NOT NULL,
    step_count             INT UNSIGNED    NOT NULL,
    completed_step_count   INT UNSIGNED    NOT NULL DEFAULT 0,
    duration_ms            BIGINT UNSIGNED NULL,
    error_code             VARCHAR(64)     NULL,
    error_message          VARCHAR(1000)   NULL,
    created_at             DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    completed_at           DATETIME(3)     NULL,
    PRIMARY KEY (id),
    KEY idx_api_execution_project_created (project_id, created_at),
    KEY idx_api_execution_environment_created (environment_id, created_at),
    CONSTRAINT fk_api_execution_project
        FOREIGN KEY (project_id) REFERENCES api_project (id),
    CONSTRAINT fk_api_execution_environment
        FOREIGN KEY (environment_id) REFERENCES api_environment (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '受控 API 场景执行记录';

CREATE TABLE api_execution_step
(
    id                       BIGINT UNSIGNED NOT NULL,
    execution_id             BIGINT UNSIGNED NOT NULL,
    step_index               INT UNSIGNED    NOT NULL,
    step_name                VARCHAR(128)    NOT NULL,
    http_method              VARCHAR(10)     NOT NULL,
    request_url              VARCHAR(1000)   NOT NULL,
    request_headers_json     JSON            NOT NULL,
    request_body_redacted    MEDIUMTEXT      NULL,
    response_status          INT             NULL,
    response_headers_json    JSON            NULL,
    response_body_redacted   MEDIUMTEXT      NULL,
    extracted_names_json     JSON            NOT NULL,
    assertions_json          JSON            NOT NULL,
    success                  TINYINT(1)       NOT NULL,
    duration_ms              BIGINT UNSIGNED NOT NULL,
    error_message            VARCHAR(1000)   NULL,
    created_at               DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_api_execution_step_index (execution_id, step_index),
    KEY idx_api_execution_step_execution (execution_id, created_at),
    CONSTRAINT fk_api_execution_step_execution
        FOREIGN KEY (execution_id) REFERENCES api_execution (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '受控 API 场景执行步骤';

UPDATE sys_setting
SET config_value = '4',
    description = 'DocHelper 数据库结构版本'
WHERE config_key = 'application.schema.version';
