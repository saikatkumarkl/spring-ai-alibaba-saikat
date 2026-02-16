/******************************************/
/*   Source System table for ManifoldCF    */
/*   integration with Admin platform       */
/******************************************/

DROP TABLE IF EXISTS source_system;
CREATE TABLE source_system
(
    id                BIGSERIAL    NOT NULL,
    source_id         VARCHAR(64)  NOT NULL,
    workspace_id      VARCHAR(64)  NOT NULL,
    name              VARCHAR(255) NOT NULL,
    description       VARCHAR(4096)         DEFAULT NULL,
    connector_type    VARCHAR(128) NOT NULL,
    connector_class   VARCHAR(512) NOT NULL,
    status            SMALLINT     NOT NULL DEFAULT 1,
    connection_config TEXT                  DEFAULT NULL,
    test_result       VARCHAR(32)           DEFAULT NULL,
    mcf_connection_name VARCHAR(255)        DEFAULT NULL,
    mcf_output_name   VARCHAR(255)          DEFAULT NULL,
    mcf_job_id        VARCHAR(64)           DEFAULT NULL,
    mcf_job_status    VARCHAR(64)           DEFAULT NULL,
    last_sync_time    TIMESTAMP             DEFAULT NULL,
    sync_cron         VARCHAR(128)          DEFAULT NULL,
    docs_total        BIGINT       NOT NULL DEFAULT 0,
    docs_processed    BIGINT       NOT NULL DEFAULT 0,
    docs_failed       BIGINT       NOT NULL DEFAULT 0,
    error_message     TEXT                  DEFAULT NULL,
    gmt_create        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creator           VARCHAR(64)  NOT NULL,
    modifier          VARCHAR(64)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (source_id)
);

CREATE INDEX idx_source_system_workspace ON source_system(workspace_id);
CREATE INDEX idx_source_system_status ON source_system(status);
