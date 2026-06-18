package com.example.wmtippspiel.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistenz der Rang-Vergleichsbasis für das Leaderboard-Board (F11). Hält je
 * Teilnehmer den Rang aus dem zuletzt abgeschlossenen Auswertungs-Batch; übersteht
 * Neustarts (FR-007). Vollständiger Ersatz pro Batch ({@link #replaceAll}).
 */
@Repository
public class LeaderboardSnapshotRepository {

    private final JdbcClient jdbc;

    public LeaderboardSnapshotRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Rang je Teilnehmer aus dem vorigen Batch (leer beim ersten Lauf). */
    public Map<String, Integer> findAllRanks() {
        Map<String, Integer> ranks = new LinkedHashMap<>();
        jdbc.sql("SELECT user_id, rank FROM leaderboard_snapshot")
                .query((rs, rowNum) -> {
                    ranks.put(rs.getString("user_id"), rs.getInt("rank"));
                    return null;
                })
                .list();
        return ranks;
    }

    /** Ersetzt die gesamte Vergleichsbasis durch die aktuellen Ränge (alle Teilnehmer). */
    @Transactional
    public void replaceAll(Map<String, Integer> ranks, Instant capturedAt) {
        jdbc.sql("DELETE FROM leaderboard_snapshot").update();
        OffsetDateTime ts = OffsetDateTime.ofInstant(capturedAt, ZoneOffset.UTC);
        for (Map.Entry<String, Integer> e : ranks.entrySet()) {
            jdbc.sql("INSERT INTO leaderboard_snapshot (user_id, rank, captured_at) VALUES (:u, :r, :t)")
                    .param("u", e.getKey())
                    .param("r", e.getValue())
                    .param("t", ts)
                    .update();
        }
    }
}
