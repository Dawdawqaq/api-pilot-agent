CREATE TABLE knowledge_document
(
    id                 BIGINT UNSIGNED NOT NULL,
    project_id         BIGINT UNSIGNED NOT NULL,
    file_name          VARCHAR(255)    NOT NULL,
    object_key         VARCHAR(512)    NOT NULL,
    content_type       VARCHAR(128)    NOT NULL,
    file_size          BIGINT UNSIGNED NOT NULL,
    content_hash       CHAR(64)        NOT NULL,
    title              VARCHAR(255)    NULL,
    status             VARCHAR(20)     NOT NULL,
    error_message      TEXT            NULL,
    chunk_count        INT UNSIGNED    NOT NULL DEFAULT 0,
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    indexed_at         DATETIME(3)     NULL,
    updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted            TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_knowledge_document_object_key (object_key),
    KEY idx_knowledge_document_project_status_created (project_id, status, created_at),
    KEY idx_knowledge_document_project_hash_status (project_id, content_hash, status),
    CONSTRAINT fk_knowledge_document_project
        FOREIGN KEY (project_id) REFERENCES api_project (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '知识库原始文档';

CREATE TABLE knowledge_chunk
(
    id            BIGINT UNSIGNED NOT NULL,
    project_id    BIGINT UNSIGNED NOT NULL,
    document_id   BIGINT UNSIGNED NOT NULL,
    chunk_index   INT UNSIGNED    NOT NULL,
    section_title VARCHAR(500)    NULL,
    content       MEDIUMTEXT      NOT NULL,
    char_count    INT UNSIGNED    NOT NULL,
    content_hash  CHAR(64)        NOT NULL,
    vector_id     VARCHAR(64)     NOT NULL,
    created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_knowledge_chunk_document_index (document_id, chunk_index),
    UNIQUE KEY uk_knowledge_chunk_vector_id (vector_id),
    KEY idx_knowledge_chunk_project_document (project_id, document_id),
    KEY idx_knowledge_chunk_project_hash (project_id, content_hash),
    CONSTRAINT fk_knowledge_chunk_project
        FOREIGN KEY (project_id) REFERENCES api_project (id),
    CONSTRAINT fk_knowledge_chunk_document
        FOREIGN KEY (document_id) REFERENCES knowledge_document (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '知识库文本切片';

CREATE TABLE retrieval_evaluation_case
(
    id                   BIGINT UNSIGNED NOT NULL,
    project_id           BIGINT UNSIGNED NOT NULL,
    case_name            VARCHAR(128)    NOT NULL,
    query_text           VARCHAR(1000)   NOT NULL,
    expected_document_id BIGINT UNSIGNED NOT NULL,
    created_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted              TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_retrieval_eval_case_project_name_deleted (project_id, case_name, deleted),
    KEY idx_retrieval_eval_case_project_created (project_id, created_at),
    CONSTRAINT fk_retrieval_eval_case_project
        FOREIGN KEY (project_id) REFERENCES api_project (id),
    CONSTRAINT fk_retrieval_eval_case_document
        FOREIGN KEY (expected_document_id) REFERENCES knowledge_document (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '检索评测用例';

CREATE TABLE retrieval_evaluation_run
(
    id             BIGINT UNSIGNED NOT NULL,
    project_id     BIGINT UNSIGNED NOT NULL,
    top_k          INT UNSIGNED    NOT NULL,
    case_count     INT UNSIGNED    NOT NULL,
    hit_count      INT UNSIGNED    NOT NULL,
    recall_at_k    DECIMAL(8, 6)   NOT NULL,
    details_json   JSON            NOT NULL,
    created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_retrieval_eval_run_project_created (project_id, created_at),
    CONSTRAINT fk_retrieval_eval_run_project
        FOREIGN KEY (project_id) REFERENCES api_project (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '检索评测运行记录';

UPDATE sys_setting
SET config_value = '3',
    description = 'DocHelper 数据库结构版本'
WHERE config_key = 'application.schema.version';
