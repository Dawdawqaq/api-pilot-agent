CREATE TABLE quality_evaluation_run
(
    id                        BIGINT UNSIGNED NOT NULL,
    dataset_version           VARCHAR(64)     NOT NULL,
    service_count             INT UNSIGNED    NOT NULL,
    evaluation_case_count     INT UNSIGNED    NOT NULL,
    security_case_count       INT UNSIGNED    NOT NULL,
    passed_case_count         INT UNSIGNED    NOT NULL,
    blocked_attack_count      INT UNSIGNED    NOT NULL,
    task_success_rate         DECIMAL(8, 6)   NOT NULL,
    valid_plan_rate           DECIMAL(8, 6)   NOT NULL,
    security_block_rate       DECIMAL(8, 6)   NOT NULL,
    p95_task_duration_ms      BIGINT UNSIGNED NOT NULL,
    total_model_tokens        BIGINT UNSIGNED NOT NULL,
    metrics_json              JSON            NOT NULL,
    created_at                DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_quality_evaluation_created (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'MVP 质量与安全评测运行';

UPDATE sys_setting
SET config_value = '14',
    description = 'DocHelper 数据库结构版本'
WHERE config_key = 'application.schema.version';
