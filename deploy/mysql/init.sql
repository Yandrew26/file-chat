-- FileChat MySQL schema (database: file_chat_db)
-- rag_chat_memory is created automatically by SelfMysqlChatMemoryRepository on first start.

CREATE TABLE IF NOT EXISTS system_user (
    id           BIGINT       NOT NULL PRIMARY KEY,
    user_id      VARCHAR(64)  NOT NULL UNIQUE,
    user_name    VARCHAR(128) NOT NULL,
    email        VARCHAR(255),
    mobile       VARCHAR(32),
    created_date DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rag_chat_history (
    id              BIGINT       NOT NULL PRIMARY KEY,
    user_id         VARCHAR(64)  NOT NULL,
    user_name       VARCHAR(128),
    conversation_id VARCHAR(256) NOT NULL,
    trace_id        VARCHAR(64),
    content         LONGTEXT,
    type            VARCHAR(16)  NOT NULL,   -- USER | ASSISTANT
    created_date    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_conversation (conversation_id, created_date),
    INDEX idx_user (user_id, created_date)
);

-- API clients allowed through the OpenAPI gateway (see AuthServiceImpl).
-- The gateway expects header `authID: <auth_id>` and
-- `authorization: md5("authId=<auth_id>&secretKey=<secret_key>")`.
CREATE TABLE IF NOT EXISTS rag_auth_base (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    auth_name      VARCHAR(128) NOT NULL,
    auth_id        VARCHAR(64)  NOT NULL UNIQUE,
    secret_key     VARCHAR(128) NOT NULL,
    enabled_status TINYINT(1)   NOT NULL DEFAULT 1,
    created_date   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_date   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Demo data (local development only).
INSERT IGNORE INTO system_user (id, user_id, user_name, email) VALUES
    (1, '12345678', 'Andrew', 'demo@example.com');

INSERT IGNORE INTO rag_auth_base (auth_name, auth_id, secret_key, enabled_status) VALUES
    ('demo-client', '12345', '54321', 1);
