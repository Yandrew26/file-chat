package com.andrewproject.filechat.config.memory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SelfMysqlChatMemoryRepository extends SelfJdbcChatMemoryRepository {

    private static final String MYSQL_QUERY_ADD = "INSERT INTO rag_chat_memory (user_id, conversation_id, content, type, timestamp) VALUES (?, ?, ?, ?, ?)";
    private static final String MYSQL_QUERY_GET = "SELECT content, type FROM rag_chat_memory WHERE conversation_id = ? ORDER BY timestamp";

    private SelfMysqlChatMemoryRepository(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    public static MysqlBuilder mysqlBuilder() {
        return new MysqlBuilder();
    }

    protected String hasTableSql(String tableName) {
        return String.format("SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = '%s'", tableName);
    }

    protected String createTableSql(String tableName) {
        return String.format("CREATE TABLE %s (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id VARCHAR(255) NOT NULL, conversation_id VARCHAR(256) NOT NULL, content LONGTEXT NOT NULL, type VARCHAR(100) NOT NULL, timestamp TIMESTAMP NOT NULL, CONSTRAINT chk_message_type_kb CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')))", tableName);
    }

    protected String getAddSql() {
        return "INSERT INTO rag_chat_memory (user_id, conversation_id, content, type, timestamp) VALUES (?, ?, ?, ?, ?)";
    }

    protected String getGetSql() {
        return "SELECT content, type FROM rag_chat_memory WHERE conversation_id = ? ORDER BY timestamp";
    }

    public static class MysqlBuilder {
        private JdbcTemplate jdbcTemplate;

        public MysqlBuilder jdbcTemplate(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
            return this;
        }

        public SelfMysqlChatMemoryRepository build() {
            return new SelfMysqlChatMemoryRepository(this.jdbcTemplate);
        }
    }
}
