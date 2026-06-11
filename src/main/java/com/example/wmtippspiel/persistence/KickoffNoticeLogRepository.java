package com.example.wmtippspiel.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Merkt sich pro Spiel, ob die "Anpfiff steht bevor"-Nachricht schon gepostet wurde (Idempotenz). */
@Repository
public class KickoffNoticeLogRepository {

    private final JdbcClient jdbc;

    public KickoffNoticeLogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean wasNotified(long matchId) {
        return jdbc.sql("SELECT COUNT(*) FROM kickoff_notice_log WHERE match_id = :m")
                .param("m", matchId)
                .query(Integer.class)
                .single() > 0;
    }

    public void markNotified(long matchId, Instant now) {
        jdbc.sql("""
                        INSERT INTO kickoff_notice_log (match_id, notified_at) VALUES (:m, :ts)
                        ON CONFLICT (match_id) DO NOTHING
                        """)
                .param("m", matchId)
                .param("ts", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .update();
    }
}
