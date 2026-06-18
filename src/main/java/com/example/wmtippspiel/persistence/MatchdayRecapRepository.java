package com.example.wmtippspiel.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Idempotenz-Marker für den Spieltags-Rückblick (F12). {@link #tryClaim} fügt den
 * Recap-Key atomar ein ({@code ON CONFLICT DO NOTHING}); nur der erste Aufruf
 * erhält {@code true} und darf posten — auch über Neustarts hinweg (FR-016).
 */
@Repository
public class MatchdayRecapRepository {

    private final JdbcClient jdbc;

    public MatchdayRecapRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Reserviert den Recap-Key; {@code true}, wenn er neu war (⇒ posten). */
    public boolean tryClaim(String recapKey, Instant postedAt) {
        int inserted = jdbc.sql("""
                        INSERT INTO matchday_recap (recap_key, posted_at)
                        VALUES (:rk, :ts)
                        ON CONFLICT (recap_key) DO NOTHING
                        """)
                .param("rk", recapKey)
                .param("ts", OffsetDateTime.ofInstant(postedAt, ZoneOffset.UTC))
                .update();
        return inserted == 1;
    }
}
