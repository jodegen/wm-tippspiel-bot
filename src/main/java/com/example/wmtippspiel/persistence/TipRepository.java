package com.example.wmtippspiel.persistence;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.example.wmtippspiel.domain.model.Tip;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Persistenz für {@link Tip}. Upsert auf {@code (user_id, match_id)} setzt
 * {@code points} bewusst nicht zurück (data-model.md).
 */
@Repository
public class TipRepository {

    private final JdbcClient jdbc;

    public TipRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public java.util.Optional<Tip> findByUserAndMatch(String userId, long matchId) {
        return jdbc.sql("SELECT * FROM tips WHERE user_id = :userId AND match_id = :matchId")
                .param("userId", userId)
                .param("matchId", matchId)
                .query(TipRepository::map)
                .optional();
    }

    public List<Tip> findByMatch(long matchId) {
        return jdbc.sql("SELECT * FROM tips WHERE match_id = :matchId ORDER BY username ASC")
                .param("matchId", matchId)
                .query(TipRepository::map)
                .list();
    }

    public void upsert(Tip tip) {
        jdbc.sql("""
                        INSERT INTO tips (user_id, match_id, username, home_score, away_score, created_at)
                        VALUES (:userId, :matchId, :username, :homeScore, :awayScore, :createdAt)
                        ON CONFLICT (user_id, match_id) DO UPDATE SET
                            username = EXCLUDED.username,
                            home_score = EXCLUDED.home_score,
                            away_score = EXCLUDED.away_score,
                            created_at = EXCLUDED.created_at
                        """)
                .param("userId", tip.userId())
                .param("matchId", tip.matchId())
                .param("username", tip.username())
                .param("homeScore", tip.homeScore())
                .param("awayScore", tip.awayScore())
                .param("createdAt", OffsetDateTime.ofInstant(tip.createdAt(), ZoneOffset.UTC))
                .update();
    }

    /**
     * Rangliste: Gesamtpunkte, Tippanzahl und exakte Treffer je Teilnehmer.
     * Sortiert nach Punkten ↓, dann exakten Treffern ↓ (FR-018/019/020). Der
     * geteilte Rang bei Gleichstand wird in der Anzeige aufgelöst.
     */
    public List<LeaderboardEntry> leaderboard() {
        return jdbc.sql("""
                        SELECT user_id,
                               MAX(username) AS username,
                               COALESCE(SUM(points), 0) AS total_points,
                               COUNT(*) AS tip_count,
                               COUNT(*) FILTER (WHERE points = 3) AS exact_hits
                        FROM tips
                        GROUP BY user_id
                        ORDER BY total_points DESC, exact_hits DESC
                        """)
                .query((rs, rowNum) -> new LeaderboardEntry(
                        rs.getString("user_id"),
                        rs.getString("username"),
                        rs.getInt("total_points"),
                        rs.getInt("tip_count"),
                        rs.getInt("exact_hits")))
                .list();
    }

    public void updatePoints(String userId, long matchId, int points) {
        jdbc.sql("UPDATE tips SET points = :points WHERE user_id = :userId AND match_id = :matchId")
                .param("points", points)
                .param("userId", userId)
                .param("matchId", matchId)
                .update();
    }

    private static Tip map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Tip(
                rs.getString("user_id"),
                rs.getLong("match_id"),
                rs.getString("username"),
                rs.getInt("home_score"),
                rs.getInt("away_score"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getInt("points"));
    }
}
