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
     * Sortiert nach Punkten ↓, dann exakten Treffern ↓ (FR-007). Der geteilte
     * Rang bei Gleichstand wird in der Anzeige aufgelöst.
     *
     * <p>Die exakten Treffer werden bewusst NICHT aus dem Punktwert abgeleitet,
     * sondern live aus dem Vergleich Tipp ↔ tatsächliches Ergebnis berechnet
     * (FR-006/006a) — damit bleibt die Statistik korrekt, unabhängig vom
     * Punkteschema. Gezählt werden nur ausgewertete Spiele ({@code m.evaluated}).
     */
    public List<LeaderboardEntry> leaderboard() {
        return jdbc.sql("""
                        SELECT t.user_id,
                               MAX(t.username) AS username,
                               COALESCE(SUM(t.points), 0) AS total_points,
                               COUNT(*) AS tip_count,
                               COUNT(*) FILTER (
                                   WHERE m.evaluated
                                     AND t.home_score = m.home_score
                                     AND t.away_score = m.away_score
                               ) AS exact_hits
                        FROM tips t
                        JOIN matches m ON m.id = t.match_id
                        GROUP BY t.user_id
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

    /**
     * Punktesumme je Teilnehmer für einen Spieltag/Recap-Key (F12), absteigend.
     * Recap-Key gruppiert über {@code matchday} ({@code md:<n>}) bzw. Fallback
     * {@code stage:<STAGE>} — konsistent zu {@link MatchRepository#findCompletedRecapKeys()}.
     */
    public List<MatchdayScore> matchdayLeaderboard(String recapKey) {
        return jdbc.sql("""
                        SELECT t.user_id,
                               MAX(t.username) AS username,
                               COALESCE(SUM(t.points), 0) AS pts
                        FROM tips t
                        JOIN matches m ON m.id = t.match_id
                        WHERE COALESCE('md:' || m.matchday, 'stage:' || m.stage) = :rk
                        GROUP BY t.user_id
                        ORDER BY pts DESC, username ASC
                        """)
                .param("rk", recapKey)
                .query((rs, rowNum) -> new MatchdayScore(
                        rs.getString("user_id"), rs.getString("username"), rs.getInt("pts")))
                .list();
    }

    /** Ausgewertete Einzeltipps eines Spieltags (F12) inkl. Ergebnis/Quoten, nach Punkten ↓. */
    public List<MatchdayTipRow> matchdayEvaluatedTips(String recapKey) {
        return jdbc.sql("""
                        SELECT t.username, t.home_score AS th, t.away_score AS ta, t.points,
                               m.home, m.away, m.home_score AS mh, m.away_score AS ma,
                               m.odds_home, m.odds_draw, m.odds_away
                        FROM tips t
                        JOIN matches m ON m.id = t.match_id
                        WHERE COALESCE('md:' || m.matchday, 'stage:' || m.stage) = :rk
                          AND m.evaluated = TRUE
                        ORDER BY t.points DESC, t.username ASC
                        """)
                .param("rk", recapKey)
                .query((rs, rowNum) -> new MatchdayTipRow(
                        rs.getString("username"),
                        rs.getInt("th"), rs.getInt("ta"), rs.getInt("points"),
                        (Integer) rs.getObject("mh"), (Integer) rs.getObject("ma"),
                        rs.getString("home"), rs.getString("away"),
                        rs.getBigDecimal("odds_home"), rs.getBigDecimal("odds_draw"), rs.getBigDecimal("odds_away")))
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
