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

--init model
INSERT INTO provider (workspace_id, icon, name, description, provider, enable, source, credential,
                                 supported_model_types, protocol, gmt_create, gmt_modified, creator, modifier)
VALUES ( '1', null, 'Tongyi', 'Tongyi', 'Tongyi', 1, 'preset','{"endpoint":"https://dashscope.aliyuncs.com/compatible-mode"}',
        null, 'OpenAI', now(), now(), null,null);

INSERT INTO model (workspace_id,icon,name,type,mode,model_id,provider,enable,tags,source,gmt_create,gmt_modified,creator,modifier) VALUES ('1',null,'qwen-max','llm','chat','qwen-max','Tongyi',1,'web_search,function_call','preset',now(),now(),null,null);

INSERT INTO model (workspace_id,icon,name,type,mode,model_id,provider,enable,tags,source,gmt_create,gmt_modified,creator,modifier) VALUES ('1',null,'qwen-max-latest','llm','chat','qwen-max-latest','Tongyi',1,'web_search,function_call,reasoning','preset',now(),now(),null,null);

INSERT INTO model (workspace_id,icon,name,type,mode,model_id,provider,enable,tags,source,gmt_create,gmt_modified,creator,modifier) VALUES ('1',null,'qwen-plus','llm','chat','qwen-plus','Tongyi',1,'web_search,function_call','preset',now(),now(),null,null);

INSERT INTO model (workspace_id,icon,name,type,mode,model_id,provider,enable,tags,source,gmt_create,gmt_modified,creator,modifier) VALUES ('1',null,'qwen-plus-latest','llm','chat','qwen-plus-latest','Tongyi',1,'web_search,function_call,reasoning','preset',now(),now(),null,null);

INSERT INTO model (workspace_id,icon,name,type,mode,model_id,provider,enable,tags,source,gmt_create,gmt_modified,creator,modifier) VALUES ('1',null,'qwen-turbo','llm','chat','qwen-turbo','Tongyi',1,'web_search,function_call','preset',now(),now(),null,null);

INSERT INTO model (workspace_id,icon,name,type,mode,model_id,provider,enable,tags,source,gmt_create,gmt_modified,creator,modifier) VALUES ('1',null,'qwen-turbo-latest','llm','chat','qwen-turbo-latest','Tongyi',1,'web_search,function_call,reasoning','preset',now(),now(),null,null);

INSERT INTO model (workspace_id,icon,name,type,mode,model_id,provider,enable,tags,source,gmt_create,gmt_modified,creator,modifier) VALUES ('1',null,'qwen3-235b-a22b','llm','chat','qwen3-235b-a22b','Tongyi',1,'function_call,reasoning','preset',now(),now(),null,null);

INSERT INTO model (workspace_id,icon,name,type,mode,model_id,provider,enable,tags,source,gmt_create,gmt_modified,creator,modifier) VALUES ('1',null,'qwen3-30b-a3b','llm','chat','qwen3-30b-a3b','Tongyi',1,'function_call,reasoning','preset',now(),now(),null,null);

INSERT INTO model (workspace_id,icon,name,type,mode,model_id,provider,enable,tags,source,gmt_create,gmt_modified,creator,modifier) VALUES ('1',null,'qwen3-32b','llm','chat','qwen3-32b','Tongyi',1,'function_call,reasoning','preset',now(),now(),null,null);

INSERT INTO model (workspace_id,icon,name,type,mode,model_id,provider,enable,tags,source,gmt_create,gmt_modified,creator,modifier) VALUES ('1',null,'qwen3-14b','llm','chat','qwen3-14b','Tongyi',1,'function_call,reasoning','preset',now(),now(),null,null);

INSERT INTO model (workspace_id,icon,name,type,mode,model_id,provider,enable,tags,source,gmt_create,gmt_modified,creator,modifier) VALUES ('1',null,'qwen3-8b','llm','chat','qwen3-8b','Tongyi',1,'function_call,reasoning','preset',now(),now(),null,null);

INSERT INTO model (workspace_id,icon,name,type,mode,model_id,provider,enable,tags,source,gmt_create,gmt_modified,creator,modifier) VALUES ('1',null,'qwen3-4b','llm','chat','qwen3-4b','Tongyi',1,'function_call,reasoning','preset',now(),now(),null,null);

INSERT INTO model (workspace_id,icon,name,type,mode,model_id,provider,enable,tags,source,gmt_create,gmt_modified,creator,modifier) VALUES ('1',null,'qwen3-1.7b','llm','chat','qwen3-1.7b','Tongyi',1,'function_call,reasoning','preset',now(),now(),null,null);

INSERT INTO model (workspace_id,icon,name,type,mode,model_id,provider,enable,tags,source,gmt_create,gmt_modified,creator,modifier) VALUES ('1',null,'qwen3-0.6b','llm','chat','qwen3-0.6b','Tongyi',1,'function_call,reasoning','preset',now(),now(),null,null);

INSERT INTO model (workspace_id,icon,name,type,mode,model_id,provider,enable,tags,source,gmt_create,gmt_modified,creator,modifier) VALUES ('1',null,'qwen-vl-max','llm','chat','qwen-vl-max','Tongyi',1,'vision,function_call','preset',now(),now(),null,null);

INSERT INTO model (workspace_id,icon,name,type,mode,model_id,provider,enable,tags,source,gmt_create,gmt_modified,creator,modifier) VALUES ('1',null,'qwen-vl-plus','llm','chat','qwen-vl-plus','Tongyi',1,'vision,function_call','preset',now(),now(),null,null);

INSERT INTO model (workspace_id,icon,name,type,mode,model_id,provider,enable,tags,source,gmt_create,gmt_modified,creator,modifier) VALUES ('1',null,'qvq-max','llm','chat','qvq-max','Tongyi',1,'vision,reasoning','preset',now(),now(),null,null);

INSERT INTO model (workspace_id,icon,name,type,mode,model_id,provider,enable,tags,source,gmt_create,gmt_modified,creator,modifier) VALUES ('1',null,'qwq-plus','llm','chat','qwq-plus','Tongyi',1,'reasoning,function_call','preset',now(),now(),null,null);

INSERT INTO model (workspace_id,icon,name,type,mode,model_id,provider,enable,tags,source,gmt_create,gmt_modified,creator,modifier) VALUES ('1',null,'text-embedding-v1','text_embedding','chat','text-embedding-v1','Tongyi',1,'embedding','preset',now(),now(),null,null);

INSERT INTO model (workspace_id,icon,name,type,mode,model_id,provider,enable,tags,source,gmt_create,gmt_modified,creator,modifier) VALUES ('1',null,'text-embedding-v2','text_embedding','chat','text-embedding-v2','Tongyi',1,'embedding','preset',now(),now(),null,null);

INSERT INTO model (workspace_id,icon,name,type,mode,model_id,provider,enable,tags,source,gmt_create,gmt_modified,creator,modifier) VALUES ('1',null,'text-embedding-v3','text_embedding','chat','text-embedding-v3','Tongyi',1,'embedding','preset',now(),now(),null,null);

INSERT INTO model (workspace_id,icon,name,type,mode,model_id,provider,enable,tags,source,gmt_create,gmt_modified,creator,modifier) VALUES ('1',null,'gte-rerank-v2','rerank','chat','gte-rerank-v2','Tongyi',1,null,'preset',now(),now(),null,null);

INSERT INTO model (workspace_id,icon,name,type,mode,model_id,provider,enable,tags,source,gmt_create,gmt_modified,creator,modifier) VALUES ('1',null,'deepseek-r1','llm','chat','deepseek-r1','Tongyi',1,'reasoning','preset',now(),now(),null,null);

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
