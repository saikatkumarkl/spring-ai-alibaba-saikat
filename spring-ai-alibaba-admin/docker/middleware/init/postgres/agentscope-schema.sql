/******************************************/
/*   table = account                      */
/******************************************/
DROP TABLE IF EXISTS account;
CREATE TABLE account
(

    id             BIGSERIAL NOT NULL ,
    account_id     VARCHAR(64)                        NOT NULL ,
    username       VARCHAR(255)                       NOT NULL ,
    email          VARCHAR(255)                                DEFAULT NULL ,
    mobile         VARCHAR(255)                                DEFAULT NULL ,
    password       VARCHAR(255)                       NOT NULL ,
    nickname       VARCHAR(255)                                DEFAULT NULL ,
    icon           VARCHAR(255)                                DEFAULT NULL ,
    type           VARCHAR(64)                        NOT NULL ,
    status         SMALLINT                         NOT NULL DEFAULT 1 ,
    gmt_create     TIMESTAMP                           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified   TIMESTAMP                           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_last_login TIMESTAMP                                    DEFAULT NULL ,
    creator        VARCHAR(64)                        NOT NULL ,
    modifier       VARCHAR(64)                        NOT NULL ,
    PRIMARY KEY (id),
    UNIQUE (account_id));

/******************************************/
/*   table = application                  */
/******************************************/
DROP TABLE IF EXISTS application;
CREATE TABLE application
(
    id           BIGSERIAL NOT NULL ,
    workspace_id VARCHAR(64)                    NOT NULL ,
    app_id       VARCHAR(64)                    NOT NULL ,
    name         VARCHAR(255)                   NOT NULL ,
    description  VARCHAR(4096)                           DEFAULT NULL ,
    icon         VARCHAR(255)                            DEFAULT NULL ,
    source       VARCHAR(64)                    NOT NULL ,
    type         VARCHAR(64)                    NOT NULL ,
    status       SMALLINT                     NOT NULL DEFAULT 1 ,
    gmt_create   TIMESTAMP                       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP                       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creator      VARCHAR(64)                    NOT NULL ,
    modifier     VARCHAR(64)                    NOT NULL ,
    PRIMARY KEY (id),
    UNIQUE (app_id));

/******************************************/
/*   table = application_version          */
/******************************************/
DROP TABLE IF EXISTS application_version;
CREATE TABLE application_version
(
    id           BIGSERIAL NOT NULL ,
    app_id       VARCHAR(64)                        NOT NULL ,
    workspace_id VARCHAR(64)                        NOT NULL ,
    config       TEXT                                    DEFAULT NULL ,
    status       SMALLINT                         NOT NULL ,
    version      VARCHAR(32)                        NOT NULL DEFAULT '0.0.1' ,
    description  VARCHAR(4096)                               DEFAULT NULL ,
    gmt_create   TIMESTAMP                           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP                           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creator      VARCHAR(64)                        NOT NULL ,
    modifier     VARCHAR(64)                        NOT NULL ,
    PRIMARY KEY (id));

/******************************************/
/*   table = workspace                    */
/******************************************/
DROP TABLE if exists workspace;
CREATE TABLE workspace
(
    id           BIGSERIAL NOT NULL ,
    workspace_id VARCHAR(64)                        NOT NULL ,
    account_id   VARCHAR(64)                        NOT NULL ,
    status       SMALLINT                         NOT NULL DEFAULT 1 ,
    name         VARCHAR(255)                       NOT NULL ,
    description  VARCHAR(4096)                               DEFAULT NULL ,
    config       TEXT                                        DEFAULT NULL ,
    gmt_create   TIMESTAMP                           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP                           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creator      VARCHAR(64)                        NOT NULL ,
    modifier     VARCHAR(64)                        NOT NULL ,
    PRIMARY KEY (id),
    UNIQUE (workspace_id));

/******************************************/
/*   table = api_key                       */
/******************************************/
DROP TABLE IF EXISTS api_key;
CREATE TABLE api_key
(
    id           BIGSERIAL NOT NULL ,
    account_id   VARCHAR(64)                        NOT NULL ,
    api_key      VARCHAR(512)                       NOT NULL ,
    status       SMALLINT                         NOT NULL DEFAULT 1 ,
    description  VARCHAR(4096)                               DEFAULT NULL ,
    gmt_create   TIMESTAMP                           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP                           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creator      VARCHAR(64)                        NOT NULL ,
    modifier     VARCHAR(64)                        NOT NULL ,
    PRIMARY KEY (id),
    UNIQUE (api_key));

/******************************************/
/*   table = plugin                       */
/******************************************/
DROP TABLE IF EXISTS plugin;
CREATE TABLE plugin
(
    id           BIGSERIAL NOT NULL ,
    plugin_id    VARCHAR(64)                        NOT NULL ,
    workspace_id VARCHAR(64)                        NOT NULL ,
    type         VARCHAR(64)                        NOT NULL ,
    status       SMALLINT                         NOT NULL DEFAULT 1 ,
    name         VARCHAR(255)                       NOT NULL ,
    description  VARCHAR(4096)                               DEFAULT NULL ,
    config       TEXT                                        DEFAULT NULL ,
    source       VARCHAR(64)                        NOT NULL ,
    gmt_create   TIMESTAMP                           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP                           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creator      VARCHAR(64)                        NOT NULL ,
    modifier     VARCHAR(64)                        NOT NULL ,
    PRIMARY KEY (id),
    UNIQUE (plugin_id));

/******************************************/
/*   table = tool                         */
/******************************************/
DROP TABLE IF EXISTS tool;
CREATE TABLE tool
(
    id           BIGSERIAL NOT NULL ,
    plugin_id    VARCHAR(64)                        NOT NULL ,
    tool_id      VARCHAR(64)                        NOT NULL ,
    workspace_id VARCHAR(64)                        NOT NULL ,
    status       SMALLINT                         NOT NULL DEFAULT 1 ,
    enabled      SMALLINT                         NOT NULL DEFAULT 1 ,
    test_status  SMALLINT                         NOT NULL DEFAULT 1 ,
    name         VARCHAR(255)                       NOT NULL ,
    description  VARCHAR(4096)                               DEFAULT NULL ,
    config       TEXT                           NOT NULL ,
    api_schema   TEXT                           NOT NULL ,
    gmt_create   TIMESTAMP                           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP                           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creator      VARCHAR(64)                        NOT NULL ,
    modifier     VARCHAR(64)                        NOT NULL ,
    PRIMARY KEY (id),
    UNIQUE (tool_id));

/******************************************/
/*   table = knowledge_base                      */
/******************************************/
DROP TABLE IF EXISTS knowledge_base;
CREATE TABLE knowledge_base
(
    id             BIGSERIAL NOT NULL ,
    workspace_id   VARCHAR(64)                        NOT NULL ,
    kb_id          VARCHAR(64)                        NOT NULL ,
    type           VARCHAR(64)                        NOT NULL ,
    status         SMALLINT                         NOT NULL DEFAULT 1 ,
    name           VARCHAR(255)                       NOT NULL ,
    description    VARCHAR(4096)                               DEFAULT NULL ,
    process_config TEXT                                        DEFAULT NULL ,
    index_config   TEXT                                        DEFAULT NULL ,
    search_config  TEXT                                        DEFAULT NULL ,
    total_docs     BIGINT                NOT NULL DEFAULT 0 ,
    gmt_create     TIMESTAMP                           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified   TIMESTAMP                           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creator        VARCHAR(64)                        NOT NULL ,
    modifier       VARCHAR(64)                        NOT NULL ,
    PRIMARY KEY (id),
    UNIQUE (kb_id));

/******************************************/
/*   table = document                      */
/******************************************/
DROP TABLE IF EXISTS document;
CREATE TABLE document
(
    id             BIGSERIAL NOT NULL ,
    workspace_id   VARCHAR(64)                        NOT NULL ,
    kb_id          VARCHAR(64)                        NOT NULL ,
    doc_id         VARCHAR(64)                        NOT NULL ,
    type           varchar(64)                        NOT NULL ,
    status         SMALLINT                         NOT NULL DEFAULT 1 ,
    enabled        SMALLINT                         NOT NULL DEFAULT 1 ,
    name           VARCHAR(255)                       NOT NULL ,
    format         VARCHAR(64)                        NOT NULL ,
    size           BIGINT                         NOT NULL DEFAULT 0 ,
    metadata       TEXT                                        DEFAULT NULL ,
    index_status   SMALLINT                         NOT NULL DEFAULT 1 ,
    path           VARCHAR(512)                       NOT NULL ,
    parsed_path    VARCHAR(512)                                DEFAULT NULL ,
    process_config TEXT                                        DEFAULT NULL ,
    source         VARCHAR(255)                                DEFAULT NULL ,
    error          TEXT                                        DEFAULT NULL ,
    gmt_create     TIMESTAMP                          NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified   TIMESTAMP                          NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creator        VARCHAR(64)                        NOT NULL ,
    modifier       VARCHAR(64)                        NOT NULL ,
    PRIMARY KEY (id),
    UNIQUE (doc_id));

/******************************************/
/*   DatabaseName = agentscope   */
/*   TableName = application_component   */
/******************************************/
DROP TABLE IF EXISTS application_component;
CREATE TABLE application_component
(
    id           BIGSERIAL NOT NULL ,
    gmt_create   TIMESTAMP        NOT NULL ,
    gmt_modified TIMESTAMP        NOT NULL ,
    code         varchar(64)     NOT NULL ,
    name         varchar(128)    NOT NULL ,
    workspace_id varchar(64)     NOT NULL ,
    type         varchar(64)     NOT NULL ,
    app_id       varchar(64)   DEFAULT NULL,
    config       TEXT ,
    description  varchar(4096) DEFAULT NULL ,
    status       SMALLINT       DEFAULT NULL ,
    creator      varchar(64)   DEFAULT NULL ,
    modifier     varchar(64)   DEFAULT NULL ,
    need_update  SMALLINT       DEFAULT NULL ,
    PRIMARY KEY (id));

/******************************************/
/*   table = reference                    */
/******************************************/
DROP TABLE IF EXISTS reference;
CREATE TABLE reference
(
    id           BIGSERIAL NOT NULL ,
    gmt_create   TIMESTAMP            NOT NULL ,
    gmt_modified TIMESTAMP            NOT NULL ,
    main_code    VARCHAR(64)         NOT NULL ,
    main_type    SMALLINT             NOT NULL ,
    refer_code   VARCHAR(64)         NOT NULL ,
    refer_type   SMALLINT             NOT NULL ,
    workspace_id VARCHAR(64)         NOT NULL DEFAULT '1' ,
    PRIMARY KEY (id))  ;

/******************************************/
/*   table = mcp_server                   */
/******************************************/
DROP TABLE IF EXISTS mcp_server;
CREATE TABLE mcp_server
(
    id            BIGSERIAL NOT NULL ,
    gmt_create    TIMESTAMP            NOT NULL ,
    gmt_modified  TIMESTAMP            NOT NULL ,
    server_code   VARCHAR(64)         NOT NULL ,
    name          VARCHAR(64)         NOT NULL ,
    description   VARCHAR(1024)       NULL ,
    source        VARCHAR(128)        NULL ,
    deploy_env    VARCHAR(16)         NULL ,
    type          VARCHAR(32)         NOT NULL ,
    deploy_config TEXT                NOT NULL ,
    workspace_id  VARCHAR(64)         NULL ,
    account_id    VARCHAR(64)         NULL ,
    status        SMALLINT             NOT NULL ,
    biz_type      VARCHAR(512)        NULL ,
    detail_config TEXT                NULL ,
    host          VARCHAR(1024)       NULL ,
    install_type  VARCHAR(32)         NULL ,
    PRIMARY KEY (id));

/******************************************/
/*   DatabaseName = agentscope   */
/*   TableName = provider   */
/******************************************/
CREATE TABLE provider
(
    id                    BIGSERIAL NOT NULL ,
    workspace_id          varchar(64)           DEFAULT NULL ,
    icon                  varchar(255)          DEFAULT NULL ,
    name                  varchar(255)          DEFAULT NULL ,
    description           varchar(1024)         DEFAULT NULL ,
    provider              varchar(255) NOT NULL ,
    enable                SMALLINT            DEFAULT '1' ,
    source                varchar(64)  NOT NULL DEFAULT 'preset' ,
    credential            varchar(1024)         DEFAULT NULL ,
    supported_model_types varchar(255)          DEFAULT NULL ,
    protocol              varchar(64)           DEFAULT NULL ,
    gmt_create            TIMESTAMP              DEFAULT CURRENT_TIMESTAMP ,
    gmt_modified          TIMESTAMP              DEFAULT CURRENT_TIMESTAMP ,
    creator               varchar(64)           DEFAULT NULL ,
    modifier              varchar(64)           DEFAULT NULL ,
    PRIMARY KEY (id));

/******************************************/
/*   DatabaseName = agentscope   */
/*   TableName = model   */
/******************************************/
CREATE TABLE model
(
    id           BIGSERIAL NOT NULL ,
    workspace_id varchar(64)           DEFAULT NULL ,
    icon         varchar(255)          DEFAULT NULL ,
    name         varchar(100)          DEFAULT NULL ,
    type         varchar(100)          DEFAULT 'LLM' ,
    mode         varchar(100)          DEFAULT 'chat' ,
    model_id     varchar(100) NOT NULL ,
    provider     varchar(100) NOT NULL ,
    enable       SMALLINT            DEFAULT '1' ,
    tags         varchar(255)          DEFAULT NULL ,
    source       varchar(100) NOT NULL DEFAULT 'preset' ,
    gmt_create   TIMESTAMP              DEFAULT CURRENT_TIMESTAMP ,
    gmt_modified TIMESTAMP              DEFAULT CURRENT_TIMESTAMP ,
    creator      varchar(64)           DEFAULT NULL ,
    modifier     varchar(64)           DEFAULT NULL ,
    PRIMARY KEY (id));

--init account
INSERT INTO account (account_id, username, email, mobile, password, type, status, gmt_create,
                     gmt_modified, creator, modifier)
VALUES ('10000', 'saa', 'ken.lj.hz@gmail.com', null,
        '$argon2id$v=19$m=66536,t=2,p=1$KSDQowfZxDjKLqBtxFNRng$znU0oQFQs2shR9la4S11n7d0LpGApmSBXvDOXuhbR40', 'admin', 1,
        now(), now(), '10000', '10000');

--init workspace
INSERT INTO workspace (workspace_id, account_id, status, name, description, config, gmt_create, gmt_modified,
                                  creator, modifier)
VALUES ('1', '10000', 1, 'Default Workspace', 'Default Workspace', null, now(), now(), '10000', '10000');

--init Ollama provider (models are auto-discovered at runtime from the endpoint)
INSERT INTO provider (workspace_id, icon, name, description, provider, enable, source, credential,
                                 supported_model_types, protocol, gmt_create, gmt_modified, creator, modifier)
VALUES ( '1', null, 'Ollama', 'Ollama local LLM server', 'ollama', 1, 'preset','{"endpoint":"http://ollama:11434"}',
        null, 'OpenAI', now(), now(), null,null);
-- No model rows needed — ModelController auto-syncs models from Ollama's /api/tags

--init Sample Chat Application (pre-configured with Ollama qwen2.5:7b)
INSERT INTO application (workspace_id, app_id, name, description, icon, source, type, status, gmt_create, gmt_modified, creator, modifier)
VALUES ('1', 'sample-chat-assistant', 'Sample Chat Assistant',
        'A sample chat application using Ollama qwen2.5:7b model. Use this as a template to create your own AI assistants.',
        null, 'preset', 'basic', 2, now(), now(), '10000', '10000');

-- Draft config version (status=1 means draft)
INSERT INTO application_version (app_id, workspace_id, config, status, version, description, gmt_create, gmt_modified, creator, modifier)
VALUES ('sample-chat-assistant', '1',
        '{"model":"qwen2.5:7b","model_provider":"ollama","instructions":"You are a helpful, accurate, and friendly AI assistant. Answer questions clearly and concisely. If you are unsure about something, say so honestly."}',
        1, '0.0.1', 'Initial sample app', now(), now(), '10000', '10000');

-- Published config version (status=2 means published)
INSERT INTO application_version (app_id, workspace_id, config, status, version, description, gmt_create, gmt_modified, creator, modifier)
VALUES ('sample-chat-assistant', '1',
        '{"model":"qwen2.5:7b","model_provider":"ollama","instructions":"You are a helpful, accurate, and friendly AI assistant. Answer questions clearly and concisely. If you are unsure about something, say so honestly."}',
        2, '0.0.1', 'Initial sample app', now(), now(), '10000', '10000');

/******************************************/
/*   table = agent_schema                 */
/******************************************/
DROP TABLE IF EXISTS agent_schema;
CREATE TABLE agent_schema
(
    id           BIGSERIAL NOT NULL ,
    agent_id     VARCHAR(64)                                 DEFAULT NULL ,
    workspace_id VARCHAR(64)                        NOT NULL ,
    name         VARCHAR(255)                       NOT NULL ,
    description  VARCHAR(4096)                               DEFAULT NULL ,
    type         VARCHAR(64)                        NOT NULL ,
    instruction  TEXT                                        DEFAULT NULL ,
    input_keys   TEXT                                        DEFAULT NULL ,
    output_key   VARCHAR(255)                                DEFAULT NULL ,
    handle       TEXT                                    DEFAULT NULL ,
    sub_agents   TEXT                                    DEFAULT NULL ,
    yaml_schema  TEXT                                    DEFAULT NULL ,
    status       VARCHAR(64)                        NOT NULL DEFAULT 'DRAFT' ,
    enabled      SMALLINT                         NOT NULL DEFAULT 1 ,
    gmt_create   TIMESTAMP                           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified TIMESTAMP                           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creator      VARCHAR(64)                        NOT NULL ,
    modifier     VARCHAR(64)                        NOT NULL ,
    PRIMARY KEY (id),
    UNIQUE (agent_id));
