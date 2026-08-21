CREATE TABLE app_user
(
    id             BIGINT UNSIGNED NOT NULL,
    username       VARCHAR(64)     NOT NULL,
    password_hash  VARCHAR(100)    NOT NULL,
    display_name   VARCHAR(100)    NOT NULL,
    status         VARCHAR(24)     NOT NULL DEFAULT 'ACTIVE',
    token_version  INT UNSIGNED    NOT NULL DEFAULT 0,
    created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    deleted        TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_username (username),
    KEY idx_app_user_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '应用用户';

CREATE TABLE project_member
(
    id          BIGINT UNSIGNED NOT NULL,
    project_id  BIGINT UNSIGNED NOT NULL,
    user_id     BIGINT UNSIGNED NOT NULL,
    role        VARCHAR(24)     NOT NULL,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_member_project_user (project_id, user_id),
    KEY idx_project_member_user_project (user_id, project_id),
    CONSTRAINT fk_project_member_project
        FOREIGN KEY (project_id) REFERENCES api_project (id),
    CONSTRAINT fk_project_member_user
        FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '项目成员与角色';

UPDATE sys_setting
SET config_value = '8',
    description = 'DocHelper 数据库结构版本'
WHERE config_key = 'application.schema.version';
