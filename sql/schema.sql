-- Schema for file_chat_db, derived from the entities in file-chat-service-search and file-chat-service-auth.
-- rag_chat_memory is created automatically by SelfMysqlChatMemoryRepository on startup.

CREATE DATABASE IF NOT EXISTS file_chat_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE file_chat_db;

-- Users who can sign in to the web client. user_id must not contain '_' (conversation ids are "<user_id>_<session>").
CREATE TABLE IF NOT EXISTS system_user (
    id           BIGINT       NOT NULL PRIMARY KEY,
    user_id      VARCHAR(64)  NOT NULL UNIQUE,
    user_name    VARCHAR(128) NOT NULL,
    email        VARCHAR(255),
    mobile       VARCHAR(32),
    created_date DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Every user question and assistant answer, written by ChatHistoryAdvisor.
CREATE TABLE IF NOT EXISTS rag_chat_history (
    id              BIGINT       NOT NULL PRIMARY KEY,
    user_id         VARCHAR(64)  NOT NULL,
    user_name       VARCHAR(128),
    conversation_id VARCHAR(160) NOT NULL,
    trace_id        VARCHAR(64),
    content         LONGTEXT,
    type            VARCHAR(16)  NOT NULL,
    created_date    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_chat_history_conversation (conversation_id, created_date),
    INDEX idx_chat_history_user (user_id, created_date)
);

-- API credentials checked by the OpenAPI gateway (authorization = md5("authId=<auth_id>&secretKey=<secret_key>")).
CREATE TABLE IF NOT EXISTS rag_auth_base (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    auth_name      VARCHAR(128),
    auth_id        VARCHAR(64)  NOT NULL UNIQUE,
    secret_key     VARCHAR(128) NOT NULL,
    enabled_status TINYINT(1)   NOT NULL DEFAULT 1,
    created_date   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_date   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Local development seed data
INSERT IGNORE INTO system_user (id, user_id, user_name, email) VALUES (1, '12345678', 'Andrew', NULL);
-- Create your own gateway API client; never commit a real id/secret. Use a long random secret, e.g. `openssl rand -hex 24`:
-- INSERT INTO rag_auth_base (auth_name, auth_id, secret_key) VALUES ('my-client', '<auth id>', '<long random secret>');
