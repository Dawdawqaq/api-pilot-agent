CREATE TABLE api_project
(
    id                BIGINT UNSIGNED NOT NULL,
    project_code      VARCHAR(64)     NOT NULL,
    project_name      VARCHAR(128)    NOT NULL,
    description       VARCHAR(500)    NULL,
    status            VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    current_import_id BIGINT UNSIGNED NULL,
    created_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted           TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_api_project_code_deleted (project_code, deleted),
    KEY idx_api_project_status_created (status, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '被测项目';

CREATE TABLE api_environment
(
    id               BIGINT UNSIGNED NOT NULL,
    project_id       BIGINT UNSIGNED NOT NULL,
    environment_name VARCHAR(64)     NOT NULL,
    base_url         VARCHAR(512)    NOT NULL,
    is_default       TINYINT(1)      NOT NULL DEFAULT 0,
    created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted          TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_api_environment_project_name_deleted (project_id, environment_name, deleted),
    KEY idx_api_environment_project_default (project_id, is_default, deleted),
    CONSTRAINT fk_api_environment_project
        FOREIGN KEY (project_id) REFERENCES api_project (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '被测项目环境';

CREATE TABLE openapi_import
(
    id                    BIGINT UNSIGNED NOT NULL,
    project_id            BIGINT UNSIGNED NOT NULL,
    revision_number       INT UNSIGNED    NOT NULL,
    retry_of_id           BIGINT UNSIGNED NULL,
    file_name             VARCHAR(255)    NOT NULL,
    content_type          VARCHAR(100)    NULL,
    content_hash          CHAR(64)        NOT NULL,
    raw_content           MEDIUMTEXT      NOT NULL,
    specification_version VARCHAR(20)     NULL,
    document_title        VARCHAR(255)    NULL,
    document_version      VARCHAR(64)     NULL,
    status                VARCHAR(20)     NOT NULL,
    error_message         TEXT            NULL,
    endpoint_count        INT UNSIGNED    NOT NULL DEFAULT 0,
    schema_count          INT UNSIGNED    NOT NULL DEFAULT 0,
    security_scheme_count INT UNSIGNED    NOT NULL DEFAULT 0,
    created_at            DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    completed_at          DATETIME(3)     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_openapi_import_project_revision (project_id, revision_number),
    KEY idx_openapi_import_project_status_created (project_id, status, created_at),
    KEY idx_openapi_import_project_hash_status (project_id, content_hash, status),
    CONSTRAINT fk_openapi_import_project
        FOREIGN KEY (project_id) REFERENCES api_project (id),
    CONSTRAINT fk_openapi_import_retry
        FOREIGN KEY (retry_of_id) REFERENCES openapi_import (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'OpenAPI 导入版本';

ALTER TABLE api_project
    ADD CONSTRAINT fk_api_project_current_import
        FOREIGN KEY (current_import_id) REFERENCES openapi_import (id);

CREATE TABLE api_endpoint
(
    id                    BIGINT UNSIGNED NOT NULL,
    project_id            BIGINT UNSIGNED NOT NULL,
    import_id             BIGINT UNSIGNED NOT NULL,
    path                  VARCHAR(512)    NOT NULL,
    http_method           VARCHAR(10)     NOT NULL,
    operation_id          VARCHAR(255)    NULL,
    summary               VARCHAR(500)    NULL,
    description           TEXT            NULL,
    tags_json             JSON            NULL,
    deprecated            TINYINT(1)      NOT NULL DEFAULT 0,
    request_body_json     JSON            NULL,
    responses_json        JSON            NULL,
    security_json         JSON            NULL,
    created_at            DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_api_endpoint_import_path_method (import_id, path, http_method),
    KEY idx_api_endpoint_project_path (project_id, path),
    KEY idx_api_endpoint_project_operation (project_id, operation_id),
    CONSTRAINT fk_api_endpoint_project
        FOREIGN KEY (project_id) REFERENCES api_project (id),
    CONSTRAINT fk_api_endpoint_import
        FOREIGN KEY (import_id) REFERENCES openapi_import (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'OpenAPI 接口定义';

CREATE TABLE api_parameter
(
    id             BIGINT UNSIGNED NOT NULL,
    endpoint_id    BIGINT UNSIGNED NOT NULL,
    parameter_name VARCHAR(255)    NOT NULL,
    location       VARCHAR(20)     NOT NULL,
    required       TINYINT(1)      NOT NULL DEFAULT 0,
    description    TEXT            NULL,
    schema_json    JSON            NULL,
    created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_api_parameter_endpoint (endpoint_id),
    KEY idx_api_parameter_name_location (parameter_name, location),
    CONSTRAINT fk_api_parameter_endpoint
        FOREIGN KEY (endpoint_id) REFERENCES api_endpoint (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'OpenAPI 接口参数';

CREATE TABLE api_schema_definition
(
    id          BIGINT UNSIGNED NOT NULL,
    project_id  BIGINT UNSIGNED NOT NULL,
    import_id   BIGINT UNSIGNED NOT NULL,
    schema_name VARCHAR(255)    NOT NULL,
    schema_json JSON            NOT NULL,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_api_schema_import_name (import_id, schema_name),
    KEY idx_api_schema_project_name (project_id, schema_name),
    CONSTRAINT fk_api_schema_project
        FOREIGN KEY (project_id) REFERENCES api_project (id),
    CONSTRAINT fk_api_schema_import
        FOREIGN KEY (import_id) REFERENCES openapi_import (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'OpenAPI Schema 定义';

CREATE TABLE api_security_scheme
(
    id                 BIGINT UNSIGNED NOT NULL,
    project_id         BIGINT UNSIGNED NOT NULL,
    import_id          BIGINT UNSIGNED NOT NULL,
    scheme_name        VARCHAR(255)    NOT NULL,
    scheme_type        VARCHAR(32)     NOT NULL,
    http_scheme        VARCHAR(32)     NULL,
    bearer_format      VARCHAR(64)     NULL,
    parameter_name     VARCHAR(255)    NULL,
    parameter_location VARCHAR(20)     NULL,
    openid_connect_url VARCHAR(512)    NULL,
    flows_json         JSON            NULL,
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_api_security_import_name (import_id, scheme_name),
    KEY idx_api_security_project_name (project_id, scheme_name),
    CONSTRAINT fk_api_security_project
        FOREIGN KEY (project_id) REFERENCES api_project (id),
    CONSTRAINT fk_api_security_import
        FOREIGN KEY (import_id) REFERENCES openapi_import (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'OpenAPI 安全方案';

UPDATE sys_setting
SET config_value = '2',
    description = 'DocHelper 数据库结构版本'
WHERE config_key = 'application.schema.version';
