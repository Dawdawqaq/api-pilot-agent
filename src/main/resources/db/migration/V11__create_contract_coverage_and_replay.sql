CREATE TABLE contract_operation_result
(
    id                     BIGINT UNSIGNED NOT NULL,
    project_id             BIGINT UNSIGNED NOT NULL,
    execution_id           BIGINT UNSIGNED NOT NULL,
    step_index             INT UNSIGNED    NOT NULL,
    endpoint_id            BIGINT UNSIGNED NOT NULL,
    operation_id           VARCHAR(200)    NULL,
    http_method            VARCHAR(10)     NOT NULL,
    path_template          VARCHAR(1000)   NOT NULL,
    response_status        INT             NOT NULL,
    contract_rules_total   INT UNSIGNED    NOT NULL,
    contract_rules_covered INT UNSIGNED    NOT NULL,
    violations_json        JSON            NOT NULL,
    created_at             DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_contract_operation_execution_step (execution_id, step_index),
    KEY idx_contract_operation_project_endpoint (project_id, endpoint_id),
    CONSTRAINT fk_contract_operation_execution FOREIGN KEY (execution_id) REFERENCES api_execution (id),
    CONSTRAINT fk_contract_operation_endpoint FOREIGN KEY (endpoint_id) REFERENCES api_endpoint (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'OpenAPI 契约校验与覆盖明细';

CREATE TABLE failure_replay_sample
(
    id                    BIGINT UNSIGNED NOT NULL,
    project_id            BIGINT UNSIGNED NOT NULL,
    execution_id          BIGINT UNSIGNED NOT NULL,
    step_index            INT UNSIGNED    NOT NULL,
    request_fingerprint   CHAR(64)        NOT NULL,
    request_json_redacted MEDIUMTEXT      NOT NULL,
    request_secret_ref    VARCHAR(128)    NOT NULL,
    error_summary         VARCHAR(1000)   NOT NULL,
    created_at            DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_replay_project_created (project_id, created_at),
    KEY idx_replay_execution_step (execution_id, step_index),
    CONSTRAINT fk_replay_project FOREIGN KEY (project_id) REFERENCES api_project (id),
    CONSTRAINT fk_replay_execution FOREIGN KEY (execution_id) REFERENCES api_execution (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '脱敏失败回放样本';

CREATE TABLE test_run_coverage
(
    id                       BIGINT UNSIGNED NOT NULL,
    project_id               BIGINT UNSIGNED NOT NULL,
    report_id                BIGINT UNSIGNED NOT NULL,
    execution_id             BIGINT UNSIGNED NOT NULL,
    operation_total          INT UNSIGNED    NOT NULL,
    operation_covered        INT UNSIGNED    NOT NULL,
    method_total             INT UNSIGNED    NOT NULL,
    method_covered           INT UNSIGNED    NOT NULL,
    documented_status_total  INT UNSIGNED    NOT NULL,
    status_covered           INT UNSIGNED    NOT NULL,
    schema_rules_total       INT UNSIGNED    NOT NULL,
    schema_rules_covered     INT UNSIGNED    NOT NULL,
    unique_server_error_count INT UNSIGNED   NOT NULL,
    created_at               DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_test_run_coverage_report (report_id),
    KEY idx_test_run_coverage_project_created (project_id, created_at),
    CONSTRAINT fk_test_run_coverage_project FOREIGN KEY (project_id) REFERENCES api_project (id),
    CONSTRAINT fk_test_run_coverage_report FOREIGN KEY (report_id) REFERENCES test_report (id),
    CONSTRAINT fk_test_run_coverage_execution FOREIGN KEY (execution_id) REFERENCES api_execution (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '测试运行覆盖率汇总';

UPDATE sys_setting
SET config_value = '11',
    description = 'DocHelper 数据库结构版本'
WHERE config_key = 'application.schema.version';
