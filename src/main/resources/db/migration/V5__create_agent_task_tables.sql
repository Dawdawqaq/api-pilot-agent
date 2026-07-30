CREATE TABLE agent_conversation
(
    id             BIGINT UNSIGNED NOT NULL,
    project_id     BIGINT UNSIGNED NOT NULL,
    title          VARCHAR(200)    NOT NULL,
    status         VARCHAR(24)     NOT NULL,
    created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    deleted        TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_agent_conversation_project_created (project_id, created_at),
    CONSTRAINT fk_agent_conversation_project
        FOREIGN KEY (project_id) REFERENCES api_project (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Agent 会话';

CREATE TABLE agent_task
(
    id                     BIGINT UNSIGNED NOT NULL,
    project_id             BIGINT UNSIGNED NOT NULL,
    environment_id         BIGINT UNSIGNED NOT NULL,
    conversation_id        BIGINT UNSIGNED NOT NULL,
    goal                   VARCHAR(2000)   NOT NULL,
    status                 VARCHAR(32)     NOT NULL,
    current_step           INT UNSIGNED    NOT NULL DEFAULT 0,
    max_steps              INT UNSIGNED    NOT NULL,
    tool_call_count        INT UNSIGNED    NOT NULL DEFAULT 0,
    replan_count           INT UNSIGNED    NOT NULL DEFAULT 0,
    plan_json              JSON            NULL,
    context_json_redacted  JSON            NOT NULL,
    result_summary         TEXT            NULL,
    error_code             VARCHAR(64)     NULL,
    error_message          VARCHAR(1000)   NULL,
    cancel_requested       TINYINT(1)      NOT NULL DEFAULT 0,
    lock_version           INT UNSIGNED    NOT NULL DEFAULT 0,
    deadline_at            DATETIME(3)     NOT NULL,
    created_at             DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    started_at             DATETIME(3)     NULL,
    completed_at           DATETIME(3)     NULL,
    updated_at             DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_agent_task_project_created (project_id, created_at),
    KEY idx_agent_task_status_updated (status, updated_at),
    KEY idx_agent_task_conversation_created (conversation_id, created_at),
    CONSTRAINT fk_agent_task_project
        FOREIGN KEY (project_id) REFERENCES api_project (id),
    CONSTRAINT fk_agent_task_environment
        FOREIGN KEY (environment_id) REFERENCES api_environment (id),
    CONSTRAINT fk_agent_task_conversation
        FOREIGN KEY (conversation_id) REFERENCES agent_conversation (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Agent 任务';

CREATE TABLE agent_task_event
(
    id             BIGINT UNSIGNED NOT NULL,
    task_id        BIGINT UNSIGNED NOT NULL,
    sequence_no    BIGINT UNSIGNED NOT NULL,
    event_type     VARCHAR(40)     NOT NULL,
    state          VARCHAR(32)     NOT NULL,
    payload_json   JSON            NOT NULL,
    created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_task_event_sequence (task_id, sequence_no),
    KEY idx_agent_task_event_task_created (task_id, created_at),
    CONSTRAINT fk_agent_task_event_task
        FOREIGN KEY (task_id) REFERENCES agent_task (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Agent 状态与执行事件';

CREATE TABLE agent_tool_call
(
    id                      BIGINT UNSIGNED NOT NULL,
    task_id                 BIGINT UNSIGNED NOT NULL,
    step_index              INT UNSIGNED    NOT NULL,
    tool_name               VARCHAR(80)     NOT NULL,
    call_key                CHAR(64)        NOT NULL,
    request_json_redacted   JSON            NOT NULL,
    response_json_redacted  MEDIUMTEXT      NULL,
    status                  VARCHAR(24)     NOT NULL,
    attempt                 INT UNSIGNED    NOT NULL,
    duration_ms             BIGINT UNSIGNED NULL,
    error_code              VARCHAR(64)     NULL,
    error_message           VARCHAR(1000)   NULL,
    created_at              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    completed_at            DATETIME(3)     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_tool_call_attempt (task_id, call_key, attempt),
    KEY idx_agent_tool_call_task_step (task_id, step_index),
    CONSTRAINT fk_agent_tool_call_task
        FOREIGN KEY (task_id) REFERENCES agent_task (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Agent 工具调用审计';

CREATE TABLE agent_confirmation
(
    id             BIGINT UNSIGNED NOT NULL,
    task_id        BIGINT UNSIGNED NOT NULL,
    step_index     INT UNSIGNED    NOT NULL,
    status         VARCHAR(24)     NOT NULL,
    request_json   JSON            NOT NULL,
    decision_note  VARCHAR(500)    NULL,
    expires_at     DATETIME(3)     NOT NULL,
    created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    decided_at     DATETIME(3)     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_confirmation_task_step (task_id, step_index),
    KEY idx_agent_confirmation_status_expiry (status, expires_at),
    CONSTRAINT fk_agent_confirmation_task
        FOREIGN KEY (task_id) REFERENCES agent_task (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Agent 危险操作人工确认';

CREATE TABLE agent_message
(
    id               BIGINT UNSIGNED NOT NULL,
    conversation_id  BIGINT UNSIGNED NOT NULL,
    task_id           BIGINT UNSIGNED NULL,
    role              VARCHAR(24)     NOT NULL,
    content_redacted  MEDIUMTEXT      NOT NULL,
    created_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_agent_message_conversation_created (conversation_id, created_at),
    KEY idx_agent_message_task_created (task_id, created_at),
    CONSTRAINT fk_agent_message_conversation
        FOREIGN KEY (conversation_id) REFERENCES agent_conversation (id),
    CONSTRAINT fk_agent_message_task
        FOREIGN KEY (task_id) REFERENCES agent_task (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Agent 会话消息';

UPDATE sys_setting
SET config_value = '5',
    description = 'DocHelper 数据库结构版本'
WHERE config_key = 'application.schema.version';
