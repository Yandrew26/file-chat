package com.andrewproject.filechat.config.memory;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.*;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component
public abstract class SelfJdbcChatMemoryRepository implements ChatMemoryRepository {
    public static final String TABLE_NAME = "rag_chat_memory";
    private static final String QUERY_GET_IDS = "SELECT DISTINCT conversation_id FROM rag_chat_memory\n";
    private static final String QUERY_ADD = "INSERT INTO rag_chat_memory (user_id, conversation_id, content, type, \"timestamp\") VALUES (?, ?, ?, ?, ?)\n";
    private static final String QUERY_GET = "SELECT content, type FROM rag_chat_memory WHERE conversation_id = ? ORDER BY \"timestamp\"\n";
    private static final String QUERY_CLEAR = "DELETE FROM rag_chat_memory WHERE conversation_id = ?";
    private final JdbcTemplate jdbcTemplate;

    public SelfJdbcChatMemoryRepository(JdbcTemplate jdbcTemplate) {
        Assert.notNull(jdbcTemplate, "jdbcTemplate cannot be null");
        this.jdbcTemplate = jdbcTemplate;
        this.checkAndCreateTable();
    }

    private void checkAndCreateTable() {
        if (!(Boolean)this.jdbcTemplate.query(this.hasTableSql("rag_chat_memory"), ResultSet::next)) {
            this.jdbcTemplate.execute(this.createTableSql("rag_chat_memory"));
        }

    }

    public List<String> findConversationIds() {
        List<String> conversationIds = (List)this.jdbcTemplate.query("SELECT DISTINCT conversation_id FROM rag_chat_memory\n", (rs) -> {
            ArrayList<String> ids = new ArrayList();

            while(rs.next()) {
                ids.add(rs.getString(1));
            }

            return ids;
        });
        return conversationIds != null ? conversationIds : List.of();
    }

    public List<Message> findByConversationId(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        return this.jdbcTemplate.query(this.getGetSql(), new MessageRowMapper(), new Object[]{conversationId});
    }

    public void saveAll(String conversationId, List<Message> messages) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        Assert.notNull(messages, "messages cannot be null");
        Assert.noNullElements(messages, "messages cannot contain null elements");
        this.deleteByConversationId(conversationId);
        String userId = "12345678";
        this.jdbcTemplate.batchUpdate(this.getAddSql(), new AddBatchPreparedStatement(userId, conversationId, messages));
    }

    public void deleteByConversationId(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        this.jdbcTemplate.update("DELETE FROM rag_chat_memory WHERE conversation_id = ?", new Object[]{conversationId});
    }

    protected String getAddSql() {
        return "INSERT INTO rag_chat_memory (user_id, conversation_id, content, type, \"timestamp\") VALUES (?, ?, ?, ?, ?)\n";
    }

    protected String getGetSql() {
        return "SELECT content, type FROM rag_chat_memory WHERE conversation_id = ? ORDER BY \"timestamp\"\n";
    }

    protected abstract String hasTableSql(String tableName);

    protected abstract String createTableSql(String tableName);

    private static record AddBatchPreparedStatement(String userId, String conversationId, List<Message> messages, AtomicLong instantSeq) implements BatchPreparedStatementSetter {
        private AddBatchPreparedStatement(String userId, String conversationId, List<Message> messages) {
            this(userId, conversationId, messages, new AtomicLong(Instant.now().toEpochMilli()));
        }

        public void setValues(PreparedStatement ps, int i) throws SQLException {
            Message message = (Message)this.messages.get(i);
            ps.setString(1, this.userId);
            ps.setString(2, this.conversationId);
            ps.setString(3, message.getText());
            ps.setString(4, message.getMessageType().name());
            ps.setTimestamp(5, new Timestamp(this.instantSeq.getAndIncrement()));
        }

        public int getBatchSize() {
            return this.messages.size();
        }
    }

    private static class MessageRowMapper implements RowMapper<Message> {
        @Nullable
        public Message mapRow(ResultSet rs, int i) throws SQLException {
            String content = rs.getString(1);
            MessageType type = MessageType.valueOf(rs.getString(2));
            Object var10000;
            switch (type) {
                case USER -> var10000 = new UserMessage(content);
                case ASSISTANT -> var10000 = new AssistantMessage(content);
                case SYSTEM -> var10000 = new SystemMessage(content);
                case TOOL -> var10000 = new ToolResponseMessage(List.of());
                default -> throw new IncompatibleClassChangeError();
            }

            return (Message)var10000;
        }
    }
}
