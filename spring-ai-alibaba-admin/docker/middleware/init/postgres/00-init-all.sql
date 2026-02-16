--
-- Comprehensive init script for Spring AI Alibaba Admin
-- Uses CREATE TABLE IF NOT EXISTS so it is safe to run on an existing database.
-- File is named 00-* so it runs first when mounted into /docker-entrypoint-initdb.d/
--

-- ========================================================
--  AgentScope core tables
-- ========================================================

CREATE TABLE IF NOT EXISTS account
(
    id             BIGSERIAL    NOT NULL,
    account_id     VARCHAR(64)  NOT NULL,
    username       VARCHAR(255) NOT NULL,
    email          VARCHAR(255)          DEFAULT NULL,
    mobile         VARCHAR(255)          DEFAULT NULL,
    password       VARCHAR(255) NOT NULL,
    nickname       VARCHAR(255)          DEFAULT NULL,
    icon           VARCHAR(255)          DEFAULT NULL,
    type           VARCHAR(64)  NOT NULL,
    status         SMALLINT     NOT NULL DEFAULT 1,
    gmt_create     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_last_login TIMESTAMP             DEFAULT NULL,
    creator        VARCHAR(64)  NOT NULL,
    modifier       VARCHAR(64)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (account_id)
);

CREATE TABLE IF NOT EXISTS workspace
(
    id           BIGSERIAL    NOT NULL,
    workspace_id VARCHAR(64)  NOT NULL,
    account_id   VARCHAR(64)  NOT NULL,
    status       SMALLINT     NOT NULL DEFAULT 1,
    name         VARCHAR(255) NOT NULL,
    description  VARCHAR(4096)         DEFAULT NULL,
    config       TEXT                  DEFAULT NULL,
    gmt_create   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creator      VARCHAR(64)  NOT NULL,
    modifier     VARCHAR(64)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (workspace_id)
);

CREATE TABLE IF NOT EXISTS application
(
    id           BIGSERIAL    NOT NULL,
    workspace_id VARCHAR(64)  NOT NULL,
    app_id       VARCHAR(64)  NOT NULL,
    name         VARCHAR(255) NOT NULL,
    description  VARCHAR(4096)         DEFAULT NULL,
    icon         VARCHAR(255)          DEFAULT NULL,
    source       VARCHAR(64)  NOT NULL,
    type         VARCHAR(64)  NOT NULL,
    status       SMALLINT     NOT NULL DEFAULT 1,
    gmt_create   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creator      VARCHAR(64)  NOT NULL,
    modifier     VARCHAR(64)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (app_id)
);

CREATE TABLE IF NOT EXISTS application_version
(
    id           BIGSERIAL   NOT NULL,
    app_id       VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    config       TEXT                 DEFAULT NULL,
    status       SMALLINT    NOT NULL,
    version      VARCHAR(32) NOT NULL DEFAULT '0.0.1',
    description  VARCHAR(4096)        DEFAULT NULL,
    gmt_create   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creator      VARCHAR(64) NOT NULL,
    modifier     VARCHAR(64) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS api_key
(
    id           BIGSERIAL    NOT NULL,
    account_id   VARCHAR(64)  NOT NULL,
    api_key      VARCHAR(512) NOT NULL,
    status       SMALLINT     NOT NULL DEFAULT 1,
    description  VARCHAR(4096)         DEFAULT NULL,
    gmt_create   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creator      VARCHAR(64)  NOT NULL,
    modifier     VARCHAR(64)  NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS plugin
(
    id           BIGSERIAL    NOT NULL,
    workspace_id VARCHAR(64)  NOT NULL,
    plugin_id    VARCHAR(64)  NOT NULL,
    name         VARCHAR(255) NOT NULL,
    description  VARCHAR(4096)         DEFAULT NULL,
    manifest     TEXT                  DEFAULT NULL,
    type         VARCHAR(64)  NOT NULL,
    status       SMALLINT     NOT NULL DEFAULT 1,
    gmt_create   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creator      VARCHAR(64)  NOT NULL,
    modifier     VARCHAR(64)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (plugin_id)
);

CREATE TABLE IF NOT EXISTS tool
(
    id           BIGSERIAL    NOT NULL,
    workspace_id VARCHAR(64)  NOT NULL,
    plugin_id    VARCHAR(64)  NOT NULL,
    tool_id      VARCHAR(64)  NOT NULL,
    name         VARCHAR(255) NOT NULL,
    description  VARCHAR(4096)         DEFAULT NULL,
    schema_def   TEXT                  DEFAULT NULL,
    status       SMALLINT     NOT NULL DEFAULT 1,
    gmt_create   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creator      VARCHAR(64)  NOT NULL,
    modifier     VARCHAR(64)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (tool_id)
);

CREATE TABLE IF NOT EXISTS knowledge_base
(
    id           BIGSERIAL    NOT NULL,
    workspace_id VARCHAR(64)  NOT NULL,
    kb_id        VARCHAR(64)  NOT NULL,
    name         VARCHAR(255) NOT NULL,
    description  VARCHAR(4096)         DEFAULT NULL,
    config       TEXT                  DEFAULT NULL,
    status       SMALLINT     NOT NULL DEFAULT 1,
    gmt_create   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creator      VARCHAR(64)  NOT NULL,
    modifier     VARCHAR(64)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (kb_id)
);

CREATE TABLE IF NOT EXISTS document
(
    id           BIGSERIAL    NOT NULL,
    workspace_id VARCHAR(64)  NOT NULL,
    kb_id        VARCHAR(64)  NOT NULL,
    doc_id       VARCHAR(64)  NOT NULL,
    name         VARCHAR(512) NOT NULL,
    file_path    VARCHAR(4096)         DEFAULT NULL,
    file_type    VARCHAR(32)           DEFAULT NULL,
    file_size    BIGINT                DEFAULT 0,
    char_num     BIGINT                DEFAULT 0,
    status       SMALLINT     NOT NULL DEFAULT 1,
    gmt_create   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creator      VARCHAR(64)  NOT NULL,
    modifier     VARCHAR(64)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (doc_id)
);

CREATE TABLE IF NOT EXISTS application_component
(
    id             BIGSERIAL    NOT NULL,
    workspace_id   VARCHAR(64)  NOT NULL,
    app_id         VARCHAR(64)  NOT NULL,
    component_id   VARCHAR(64)  NOT NULL,
    component_type VARCHAR(64)  NOT NULL,
    ref_id         VARCHAR(64)  NOT NULL,
    name           VARCHAR(255) NOT NULL,
    config         TEXT                  DEFAULT NULL,
    status         SMALLINT     NOT NULL DEFAULT 1,
    gmt_create     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creator        VARCHAR(64)  NOT NULL,
    modifier       VARCHAR(64)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (component_id)
);

CREATE TABLE IF NOT EXISTS reference
(
    id           BIGSERIAL   NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    ref_id       VARCHAR(64) NOT NULL,
    ref_type     VARCHAR(64) NOT NULL,
    ref_target   VARCHAR(64) NOT NULL,
    gmt_create   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creator      VARCHAR(64) NOT NULL,
    modifier     VARCHAR(64) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS mcp_server
(
    id           BIGSERIAL    NOT NULL,
    workspace_id VARCHAR(64)  NOT NULL,
    server_id    VARCHAR(64)  NOT NULL,
    name         VARCHAR(255) NOT NULL,
    description  VARCHAR(4096)         DEFAULT NULL,
    url          VARCHAR(4096)         DEFAULT NULL,
    config       TEXT                  DEFAULT NULL,
    status       SMALLINT     NOT NULL DEFAULT 1,
    gmt_create   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creator      VARCHAR(64)  NOT NULL,
    modifier     VARCHAR(64)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (server_id)
);

CREATE TABLE IF NOT EXISTS provider
(
    id           BIGSERIAL    NOT NULL,
    workspace_id VARCHAR(64)  NOT NULL,
    provider_id  VARCHAR(64)  NOT NULL,
    name         VARCHAR(255) NOT NULL,
    description  VARCHAR(4096)         DEFAULT NULL,
    config       TEXT                  DEFAULT NULL,
    status       SMALLINT     NOT NULL DEFAULT 1,
    gmt_create   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creator      VARCHAR(64)  NOT NULL,
    modifier     VARCHAR(64)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (provider_id)
);

CREATE TABLE IF NOT EXISTS model
(
    id           BIGSERIAL    NOT NULL,
    workspace_id VARCHAR(64)  NOT NULL,
    model_id     VARCHAR(64)  NOT NULL,
    provider_id  VARCHAR(64)  NOT NULL,
    name         VARCHAR(255) NOT NULL,
    description  VARCHAR(4096)         DEFAULT NULL,
    config       TEXT                  DEFAULT NULL,
    type         VARCHAR(64)  NOT NULL,
    status       SMALLINT     NOT NULL DEFAULT 1,
    gmt_create   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creator      VARCHAR(64)  NOT NULL,
    modifier     VARCHAR(64)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (model_id)
);

CREATE TABLE IF NOT EXISTS agent_schema
(
    id           BIGSERIAL   NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    app_id       VARCHAR(64) NOT NULL,
    schema_def   TEXT                 DEFAULT NULL,
    gmt_create   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creator      VARCHAR(64) NOT NULL,
    modifier     VARCHAR(64) NOT NULL,
    PRIMARY KEY (id)
);

-- ========================================================
--  Source System (ManifoldCF integration)
-- ========================================================

CREATE TABLE IF NOT EXISTS source_system
(
    id                  BIGSERIAL    NOT NULL,
    source_id           VARCHAR(64)  NOT NULL,
    workspace_id        VARCHAR(64)  NOT NULL,
    name                VARCHAR(255) NOT NULL,
    description         VARCHAR(4096)         DEFAULT NULL,
    connector_type      VARCHAR(128) NOT NULL,
    connector_class     VARCHAR(512) NOT NULL,
    status              SMALLINT     NOT NULL DEFAULT 1,
    connection_config   TEXT                  DEFAULT NULL,
    test_result         VARCHAR(32)           DEFAULT NULL,
    mcf_connection_name VARCHAR(255)          DEFAULT NULL,
    mcf_output_name     VARCHAR(255)          DEFAULT NULL,
    mcf_job_id          VARCHAR(64)           DEFAULT NULL,
    mcf_job_status      VARCHAR(64)           DEFAULT NULL,
    last_sync_time      TIMESTAMP             DEFAULT NULL,
    sync_cron           VARCHAR(128)          DEFAULT NULL,
    docs_total          BIGINT       NOT NULL DEFAULT 0,
    docs_processed      BIGINT       NOT NULL DEFAULT 0,
    docs_failed         BIGINT       NOT NULL DEFAULT 0,
    error_message       TEXT                  DEFAULT NULL,
    gmt_create          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creator             VARCHAR(64)  NOT NULL,
    modifier            VARCHAR(64)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (source_id)
);
CREATE INDEX IF NOT EXISTS idx_source_system_workspace ON source_system(workspace_id);
CREATE INDEX IF NOT EXISTS idx_source_system_status ON source_system(status);

-- ========================================================
--  Destination (OpenSearch / vector store targets)
-- ========================================================

CREATE TABLE IF NOT EXISTS destination
(
    id                BIGSERIAL    NOT NULL,
    destination_id    VARCHAR(64)  NOT NULL,
    workspace_id      VARCHAR(64)           DEFAULT NULL,
    name              VARCHAR(255) NOT NULL,
    description       TEXT                  DEFAULT NULL,
    provider_type     VARCHAR(64)  NOT NULL DEFAULT 'opensearch',
    status            INTEGER               DEFAULT 0,
    connection_config TEXT                  DEFAULT NULL,
    test_result       TEXT                  DEFAULT NULL,
    gmt_create        TIMESTAMP             DEFAULT CURRENT_TIMESTAMP,
    gmt_modified      TIMESTAMP             DEFAULT CURRENT_TIMESTAMP,
    creator           VARCHAR(128)          DEFAULT NULL,
    modifier          VARCHAR(128)          DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE (destination_id)
);

-- ========================================================
--  Knowledge Sync (source→destination pipeline)
-- ========================================================

CREATE TABLE IF NOT EXISTS knowledge_sync
(
    id             BIGSERIAL    NOT NULL,
    sync_id        VARCHAR(64)  NOT NULL,
    workspace_id   VARCHAR(64)           DEFAULT NULL,
    kb_id          VARCHAR(64)  NOT NULL,
    source_id      VARCHAR(64)           DEFAULT NULL,
    destination_id VARCHAR(64)           DEFAULT NULL,
    sync_cron      VARCHAR(128)          DEFAULT NULL,
    index_name           VARCHAR(255)          DEFAULT NULL,
    authority_index_name VARCHAR(255)          DEFAULT NULL,
    rag_index_name       VARCHAR(255)          DEFAULT NULL,
    mcf_job_id     VARCHAR(64)           DEFAULT NULL,
    status         VARCHAR(32)           DEFAULT 'pending',
    index_progress INTEGER               DEFAULT 0,
    rag_progress   INTEGER               DEFAULT 0,
    total_docs     INTEGER               DEFAULT 0,
    indexed_docs   INTEGER               DEFAULT 0,
    rag_docs       INTEGER               DEFAULT 0,
    failed_docs    INTEGER               DEFAULT 0,
    error_message  TEXT                  DEFAULT NULL,
    last_sync_time TIMESTAMP             DEFAULT NULL,
    gmt_create     TIMESTAMP             DEFAULT CURRENT_TIMESTAMP,
    gmt_modified   TIMESTAMP             DEFAULT CURRENT_TIMESTAMP,
    creator        VARCHAR(64)           DEFAULT NULL,
    modifier       VARCHAR(64)           DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE (sync_id)
);

-- ========================================================
--  Admin module tables (dataset, evaluator, experiment, prompt)
-- ========================================================

CREATE TABLE IF NOT EXISTS dataset
(
    id             BIGSERIAL    NOT NULL,
    name           VARCHAR(255) NOT NULL,
    description    TEXT                  DEFAULT NULL,
    columns_config TEXT                  DEFAULT NULL,
    create_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS dataset_version
(
    id            BIGSERIAL   NOT NULL,
    dataset_id    BIGINT      NOT NULL,
    version       VARCHAR(32) NOT NULL,
    description   TEXT                 DEFAULT NULL,
    data_count    INTEGER     NOT NULL DEFAULT 0,
    status        VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    experiments   TEXT                 DEFAULT NULL,
    dataset_items TEXT                 DEFAULT NULL,
    create_time   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (dataset_id, version)
);

CREATE TABLE IF NOT EXISTS dataset_item
(
    id             BIGSERIAL NOT NULL,
    dataset_id     BIGINT    NOT NULL,
    columns_config TEXT               DEFAULT NULL,
    data_content   TEXT      NOT NULL,
    create_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT  NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS evaluator
(
    id          BIGSERIAL    NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description TEXT                  DEFAULT NULL,
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS evaluator_version
(
    id           BIGSERIAL   NOT NULL,
    evaluator_id BIGINT      NOT NULL,
    description  TEXT                 DEFAULT NULL,
    version      VARCHAR(32) NOT NULL,
    model_config TEXT        NOT NULL,
    prompt       TEXT                 DEFAULT NULL,
    variables    TEXT                 DEFAULT NULL,
    status       VARCHAR(32)          DEFAULT NULL,
    experiments  TEXT                 DEFAULT NULL,
    create_time  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (evaluator_id, version)
);

CREATE TABLE IF NOT EXISTS evaluator_template
(
    id                     BIGSERIAL    NOT NULL,
    evaluator_template_key VARCHAR(255) NOT NULL,
    template_desc          VARCHAR(255)          DEFAULT NULL,
    template               TEXT,
    variables              TEXT                  DEFAULT NULL,
    model_config           TEXT                  DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE (evaluator_template_key)
);

CREATE TABLE IF NOT EXISTS experiment
(
    id                       BIGSERIAL    NOT NULL,
    name                     VARCHAR(255) NOT NULL,
    description              TEXT                  DEFAULT NULL,
    dataset_id               BIGINT       NOT NULL,
    dataset_version_id       BIGINT       NOT NULL,
    dataset_version          VARCHAR(32)  NOT NULL,
    evaluation_object_config TEXT                  DEFAULT NULL,
    evaluator_config         TEXT         NOT NULL,
    status                   VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    progress                 INTEGER      NOT NULL DEFAULT 0,
    complete_time            TIMESTAMP             DEFAULT NULL,
    create_time              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS experiment_result
(
    id                   BIGSERIAL NOT NULL,
    experiment_id        BIGINT    NOT NULL,
    input                TEXT      NOT NULL,
    actual_output        TEXT      NOT NULL,
    reference_output     TEXT,
    score                DECIMAL(3, 2)      DEFAULT NULL,
    reason               TEXT               DEFAULT NULL,
    evaluation_time      TIMESTAMP          DEFAULT NULL,
    evaluator_version_id BIGINT    NOT NULL,
    create_time          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS prompt
(
    id             BIGSERIAL    NOT NULL,
    prompt_key     VARCHAR(255) NOT NULL,
    prompt_desc    VARCHAR(255)          DEFAULT NULL,
    latest_version VARCHAR(32)           DEFAULT NULL,
    tags           VARCHAR(255)          DEFAULT NULL,
    create_time    TIMESTAMP(3)          DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP(3)          DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (prompt_key)
);

CREATE TABLE IF NOT EXISTS prompt_version
(
    id               BIGSERIAL    NOT NULL,
    version          VARCHAR(32)  NOT NULL,
    prompt_key       VARCHAR(255) NOT NULL,
    version_desc     VARCHAR(255)          DEFAULT NULL,
    template         TEXT,
    variables        TEXT                  DEFAULT NULL,
    model_config     TEXT                  DEFAULT NULL,
    status           VARCHAR(32)  NOT NULL DEFAULT 'pre',
    create_time      TIMESTAMP(3)          DEFAULT CURRENT_TIMESTAMP,
    previous_version VARCHAR(32)           DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE (prompt_key, version)
);

CREATE TABLE IF NOT EXISTS prompt_build_template
(
    id                  BIGSERIAL    NOT NULL,
    prompt_template_key VARCHAR(255) NOT NULL,
    tags                VARCHAR(255)          DEFAULT NULL,
    template_desc       VARCHAR(255)          DEFAULT NULL,
    template            TEXT,
    variables           TEXT                  DEFAULT NULL,
    model_config        TEXT                  DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE (prompt_template_key)
);

CREATE TABLE IF NOT EXISTS model_config
(
    id                   BIGSERIAL    NOT NULL,
    name                 VARCHAR(100) NOT NULL,
    provider             VARCHAR(50)  NOT NULL,
    model_name           VARCHAR(100) NOT NULL,
    base_url             VARCHAR(500) NOT NULL,
    api_key              VARCHAR(500) NOT NULL,
    default_parameters   JSON,
    supported_parameters JSON,
    status               SMALLINT     NOT NULL DEFAULT 1,
    create_time          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted              SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE (name)
);

-- ========================================================
--  App user access & simple users (chatbot auth)
-- ========================================================

CREATE TABLE IF NOT EXISTS app_user_access
(
    id           BIGSERIAL   NOT NULL,
    app_id       VARCHAR(64) NOT NULL,
    user_email   VARCHAR(255) NOT NULL,
    created_time TIMESTAMP            DEFAULT CURRENT_TIMESTAMP,
    created_by   VARCHAR(64)          DEFAULT 'system',
    PRIMARY KEY (id),
    UNIQUE (app_id, user_email)
);
CREATE INDEX IF NOT EXISTS idx_app_user_access_app_id ON app_user_access(app_id);
CREATE INDEX IF NOT EXISTS idx_app_user_access_email ON app_user_access(user_email);

CREATE TABLE IF NOT EXISTS simple_users
(
    id            BIGSERIAL    NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255),
    created_time  TIMESTAMP             DEFAULT CURRENT_TIMESTAMP,
    last_login    TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (email)
);

-- ========================================================
--  Chatbot tables (history, audit, file uploads)
-- ========================================================

CREATE TABLE IF NOT EXISTS chat_history
(
    id              BIGSERIAL    NOT NULL,
    user_email      VARCHAR(255) NOT NULL,
    app_id          VARCHAR(255) NOT NULL,
    conversation_id VARCHAR(255) NOT NULL,
    role            VARCHAR(50)  NOT NULL,
    content         TEXT         NOT NULL,
    created_at      TIMESTAMP             DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_chat_history_conversation ON chat_history(conversation_id);
CREATE INDEX IF NOT EXISTS idx_chat_history_user_app ON chat_history(user_email, app_id);

CREATE TABLE IF NOT EXISTS audit_log
(
    id            BIGSERIAL    NOT NULL,
    user_email    VARCHAR(255) NOT NULL,
    action        VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100),
    resource_id   VARCHAR(255),
    details       TEXT,
    ip_address    VARCHAR(50),
    created_at    TIMESTAMP             DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_audit_log_user ON audit_log(user_email);
CREATE INDEX IF NOT EXISTS idx_audit_log_action ON audit_log(action);
CREATE INDEX IF NOT EXISTS idx_audit_log_created ON audit_log(created_at DESC);

CREATE TABLE IF NOT EXISTS uploaded_files
(
    id              BIGSERIAL     NOT NULL,
    user_email      VARCHAR(255)  NOT NULL,
    conversation_id VARCHAR(255)  NOT NULL,
    file_name       VARCHAR(500)  NOT NULL,
    file_path       VARCHAR(1000) NOT NULL,
    file_size       BIGINT,
    content_type    VARCHAR(255),
    extracted_text  TEXT,
    created_at      TIMESTAMP              DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_uploaded_files_user ON uploaded_files(user_email);
CREATE INDEX IF NOT EXISTS idx_uploaded_files_conv ON uploaded_files(conversation_id);

-- ========================================================
--  Seed data: default account + workspace
-- ========================================================

INSERT INTO account (account_id, username, password, type, status, creator, modifier)
VALUES ('10000', 'saa', '$2a$10$HUhIjh5/hNwuwJWRBDq2sOOlkVJPwSqIWD8Ij2.wPlBpF04Yv5b9i', 'admin', 1, '10000', '10000')
ON CONFLICT (account_id) DO NOTHING;

INSERT INTO workspace (workspace_id, account_id, name, creator, modifier)
VALUES ('1', '10000', 'default', '10000', '10000')
ON CONFLICT (workspace_id) DO NOTHING;

-- Demo users for chatbot (password: 12345)
INSERT INTO simple_users (email, password_hash, full_name)
VALUES
    ('john@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye7WJ8RUe0LhqDJCKN9OXS9lMi0D8QyiO', 'John Doe'),
    ('jane@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye7WJ8RUe0LhqDJCKN9OXS9lMi0D8QyiO', 'Jane Smith'),
    ('test@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye7WJ8RUe0LhqDJCKN9OXS9lMi0D8QyiO', 'Test User')
ON CONFLICT (email) DO NOTHING;
