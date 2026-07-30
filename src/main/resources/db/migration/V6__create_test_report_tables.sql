CREATE TABLE test_report
(
    id                    BIGINT UNSIGNED NOT NULL,
    project_id            BIGINT UNSIGNED NOT NULL,
    task_id               BIGINT UNSIGNED NOT NULL,
    execution_id          BIGINT UNSIGNED NULL,
    title                 VARCHAR(300)    NOT NULL,
    status                VARCHAR(24)     NOT NULL,
    summary               TEXT            NOT NULL,
    total_steps           INT UNSIGNED    NOT NULL DEFAULT 0,
    passed_steps          INT UNSIGNED    NOT NULL DEFAULT 0,
    failed_steps          INT UNSIGNED    NOT NULL DEFAULT 0,
    total_tool_calls      INT UNSIGNED    NOT NULL DEFAULT 0,
    duration_ms           BIGINT UNSIGNED NOT NULL DEFAULT 0,
    evidence_json         JSON            NOT NULL,
    metrics_json          JSON            NOT NULL,
    created_at            DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_test_report_task (task_id),
    KEY idx_test_report_project_created (project_id, created_at),
    KEY idx_test_report_execution (execution_id),
    CONSTRAINT fk_test_report_project
        FOREIGN KEY (project_id) REFERENCES api_project (id),
    CONSTRAINT fk_test_report_task
        FOREIGN KEY (task_id) REFERENCES agent_task (id),
    CONSTRAINT fk_test_report_execution
        FOREIGN KEY (execution_id) REFERENCES api_execution (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Agent 测试报告';

CREATE TABLE test_report_step
(
    id                       BIGINT UNSIGNED NOT NULL,
    report_id                BIGINT UNSIGNED NOT NULL,
    execution_step_id        BIGINT UNSIGNED NOT NULL,
    step_index               INT UNSIGNED    NOT NULL,
    step_name                VARCHAR(128)    NOT NULL,
    http_method              VARCHAR(10)     NOT NULL,
    request_url              VARCHAR(1000)   NOT NULL,
    request_headers_json     JSON            NOT NULL,
    request_body_redacted    MEDIUMTEXT      NULL,
    response_status          INT             NULL,
    response_headers_json    JSON            NULL,
    response_body_redacted   MEDIUMTEXT      NULL,
    assertions_json          JSON            NOT NULL,
    success                  TINYINT(1)       NOT NULL,
    duration_ms              BIGINT UNSIGNED NOT NULL,
    error_message            VARCHAR(1000)   NULL,
    created_at               DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_test_report_step_index (report_id, step_index),
    KEY idx_test_report_step_execution (execution_step_id),
    CONSTRAINT fk_test_report_step_report
        FOREIGN KEY (report_id) REFERENCES test_report (id),
    CONSTRAINT fk_test_report_step_execution
        FOREIGN KEY (execution_step_id) REFERENCES api_execution_step (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '测试报告步骤快照';

UPDATE sys_setting
SET config_value = '6',
    description = 'DocHelper 数据库结构版本'
WHERE config_key = 'application.schema.version';
