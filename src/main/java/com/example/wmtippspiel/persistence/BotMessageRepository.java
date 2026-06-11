package com.example.wmtippspiel.persistence;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import com.example.wmtippspiel.domain.model.BotMessage;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Persistenz für getrackte Board-Nachrichten (F7 / Recovery, FR-022/027). */
@Repository
public class BotMessageRepository {

    private final JdbcClient jdbc;

    public BotMessageRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<BotMessage> findByKey(String key) {
        return jdbc.sql("SELECT * FROM bot_messages WHERE key = :key")
                .param("key", key)
                .query(BotMessageRepository::map)
                .optional();
    }

    public void upsert(BotMessage msg) {
        jdbc.sql("""
                        INSERT INTO bot_messages (key, channel_id, message_id, updated_at)
                        VALUES (:key, :channelId, :messageId, :updatedAt)
                        ON CONFLICT (key) DO UPDATE SET
                            channel_id = EXCLUDED.channel_id,
                            message_id = EXCLUDED.message_id,
                            updated_at = EXCLUDED.updated_at
                        """)
                .param("key", msg.key())
                .param("channelId", msg.channelId())
                .param("messageId", msg.messageId())
                .param("updatedAt", OffsetDateTime.ofInstant(msg.updatedAt(), ZoneOffset.UTC))
                .update();
    }

    private static BotMessage map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new BotMessage(
                rs.getString("key"),
                rs.getString("channel_id"),
                rs.getString("message_id"),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
