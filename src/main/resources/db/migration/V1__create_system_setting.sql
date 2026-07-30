CREATE TABLE sys_setting
(
    id           BIGINT UNSIGNED NOT NULL,
    config_key   VARCHAR(128)    NOT NULL,
    config_value VARCHAR(512)    NOT NULL,
    description  VARCHAR(255)    NULL,
    created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted      TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_setting_config_key (config_key),
    KEY idx_sys_setting_updated_at (updated_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '系统设置';

INSERT INTO sys_setting (id, config_key, config_value, description)
VALUES (1, 'application.schema.version', '1', 'DocHelper 数据库结构版本');
