ALTER TABLE agent_task
    ADD COLUMN lease_owner VARCHAR(100) NULL AFTER lock_version,
    ADD COLUMN lease_until DATETIME(3) NULL AFTER lease_owner,
    ADD COLUMN claimed_at DATETIME(3) NULL AFTER lease_until,
    ADD KEY idx_agent_task_lease (status, lease_until);

CREATE TABLE runtime_secret
(
    secret_ref       VARCHAR(128)    NOT NULL,
    scope_hash       CHAR(64)        NOT NULL,
    encrypted_value  MEDIUMBLOB      NOT NULL,
    initialization_vector VARBINARY(32) NOT NULL,
    expires_at       DATETIME(3)     NOT NULL,
    created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (secret_ref),
    KEY idx_runtime_secret_scope (scope_hash),
    KEY idx_runtime_secret_expiry (expires_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'AES-GCM 加密的运行时敏感值';

CREATE TABLE project_model_policy
(
    project_id               BIGINT UNSIGNED NOT NULL,
    external_model_allowed   TINYINT(1)      NOT NULL DEFAULT 1,
    allowed_provider         VARCHAR(64)     NOT NULL DEFAULT 'ANY',
    allow_document_content   TINYINT(1)      NOT NULL DEFAULT 1,
    allow_schema_content     TINYINT(1)      NOT NULL DEFAULT 1,
    prompt_character_budget  INT UNSIGNED    NOT NULL DEFAULT 40000,
    endpoint_top_k           INT UNSIGNED    NOT NULL DEFAULT 12,
    created_at               DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at               DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (project_id),
    CONSTRAINT fk_project_model_policy_project FOREIGN KEY (project_id) REFERENCES api_project (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '项目级模型数据出站策略';

UPDATE sys_setting
SET config_value = '13',
    description = 'DocHelper 数据库结构版本'
WHERE config_key = 'application.schema.version';
