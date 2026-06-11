package com.example.wmtippspiel.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Merkt sich pro Spiel, ob die Tipp-Erinnerung bereits verschickt wurde (Idempotenz). */
@Repository
public class ReminderLogRepository {

    private final JdbcClient jdbc;

    public ReminderLogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean wasReminded(long matchId) {
        return jdbc.sql("SELECT COUNT(*) FROM reminder_log WHERE match_id = :m")
                .param("m", matchId)
                .query(Integer.class)
                .single() > 0;
    }

    public void markReminded(long matchId, Instant now) {
        jdbc.sql("""
                        INSERT INTO reminder_log (match_id, reminded_at) VALUES (:m, :ts)
                        ON CONFLICT (match_id) DO NOTHING
                        """)
                .param("m", matchId)
                .param("ts", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .update();
    }
}
