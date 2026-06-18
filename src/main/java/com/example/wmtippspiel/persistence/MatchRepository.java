package com.example.wmtippspiel.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.MatchStatus;
import com.example.wmtippspiel.domain.model.Stage;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Persistenz für {@link Match} über {@link JdbcClient} (research.md R1).
 * Upsert überschreibt bewusst NICHT {@code channel}, {@code odds_*},
 * {@code revealed}, {@code evaluated} (data-model.md).
 */
@Repository
public class MatchRepository {

    private final JdbcClient jdbc;

    public MatchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Match> findById(long id) {
        return jdbc.sql("SELECT * FROM matches WHERE id = :id")
                .param("id", id)
                .query(MatchRepository::map)
                .optional();
    }

    /** Spiele, die noch nicht offengelegt und nicht abgesagt sind (Kandidaten für Reveal). */
    public List<Match> findUnrevealed() {
        return jdbc.sql("SELECT * FROM matches WHERE revealed = FALSE AND status <> 'CANCELLED'")
                .query(MatchRepository::map)
                .list();
    }

    /** Beendete, noch nicht ausgewertete Spiele (Kandidaten für Auswertung). */
    public List<Match> findUnevaluatedFinished() {
        return jdbc.sql("SELECT * FROM matches WHERE status = 'FINISHED' AND evaluated = FALSE")
                .query(MatchRepository::map)
                .list();
    }

    /** Bereits ausgewertete Spiele (Basis der rückwirkenden Punkte-Neuberechnung, F-006). */
    public List<Match> findEvaluated() {
        return jdbc.sql("SELECT * FROM matches WHERE evaluated = TRUE")
                .query(MatchRepository::map)
                .list();
    }

    /** Tippbare Spiele (Zukunft, Teams bekannt, nicht abgesagt), nach Anpfiff sortiert. */
    public List<Match> findTippable(Instant now, int limit) {
        return jdbc.sql("""
                        SELECT * FROM matches
                        WHERE kickoff > :now
                          AND status <> 'CANCELLED'
                          AND home <> 'TBD' AND away <> 'TBD'
                        ORDER BY kickoff ASC
                        LIMIT :limit
                        """)
                .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .param("limit", limit)
                .query(MatchRepository::map)
                .list();
    }

    /** Anstehende Spiele (Zukunft, nicht laufend/beendet/abgesagt), nach Anpfiff sortiert (F1/F2). */
    public List<Match> findUpcoming(Instant now, int limit) {
        return jdbc.sql("""
                        SELECT * FROM matches
                        WHERE kickoff > :now
                          AND status NOT IN ('IN_PLAY', 'FINISHED', 'CANCELLED')
                        ORDER BY kickoff ASC
                        LIMIT :limit
                        """)
                .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .param("limit", limit)
                .query(MatchRepository::map)
                .list();
    }

    /** Aktuell laufende Spiele (Status IN_PLAY), nach Anpfiff sortiert (F9 — LIVE-Bestimmung). */
    public List<Match> findInPlay() {
        return jdbc.sql("SELECT * FROM matches WHERE status = 'IN_PLAY' ORDER BY kickoff ASC")
                .query(MatchRepository::map)
                .list();
    }

    /** Alle Spiele in einem Zeitfenster (für die Tages-Slots des Boards), sortiert. */
    public List<Match> findBetween(Instant fromInclusive, Instant toExclusive) {
        return jdbc.sql("""
                        SELECT * FROM matches
                        WHERE kickoff >= :from AND kickoff < :to AND status <> 'CANCELLED'
                        ORDER BY kickoff ASC
                        """)
                .param("from", OffsetDateTime.ofInstant(fromInclusive, ZoneOffset.UTC))
                .param("to", OffsetDateTime.ofInstant(toExclusive, ZoneOffset.UTC))
                .query(MatchRepository::map)
                .list();
    }

    /** Spiele einer Gruppe (Board-Filter), sortiert. */
    public List<Match> findByGroupLabel(String groupLabel) {
        return jdbc.sql("SELECT * FROM matches WHERE group_label = :g AND status <> 'CANCELLED' ORDER BY kickoff ASC")
                .param("g", groupLabel)
                .query(MatchRepository::map)
                .list();
    }

    /** K.o.-Spiele (alle Phasen außer Gruppenphase), sortiert (Board-Filter). */
    public List<Match> findKnockout() {
        return jdbc.sql("SELECT * FROM matches WHERE stage <> 'GROUP_STAGE' AND status <> 'CANCELLED' ORDER BY kickoff ASC")
                .query(MatchRepository::map)
                .list();
    }

    /** Findet das zuletzt angesetzte Spiel einer Begegnung (für Quoten-Zuordnung). */
    public Optional<Match> findByTeams(String home, String away) {
        return jdbc.sql("""
                        SELECT * FROM matches
                        WHERE home = :home AND away = :away AND status <> 'CANCELLED'
                        ORDER BY kickoff DESC
                        LIMIT 1
                        """)
                .param("home", home)
                .param("away", away)
                .query(MatchRepository::map)
                .optional();
    }

    /** Projektion des zuletzt gemeldeten Standes (F8). */
    public record NotifiedScore(int home, int away) {
    }

    /** Liefert den zuletzt für Tor-Pings gemeldeten Stand; leer, wenn das Spiel (noch) nicht in der DB ist. */
    public Optional<NotifiedScore> getNotifiedScore(long matchId) {
        return jdbc.sql("SELECT notified_home, notified_away FROM matches WHERE id = :id")
                .param("id", matchId)
                .query((rs, rowNum) -> new NotifiedScore(rs.getInt("notified_home"), rs.getInt("notified_away")))
                .optional();
    }

    public void updateNotifiedScore(long matchId, int home, int away) {
        jdbc.sql("UPDATE matches SET notified_home = :h, notified_away = :a WHERE id = :id")
                .param("h", home).param("a", away).param("id", matchId)
                .update();
    }

    /**
     * Hält den Live-Stand+Status während eines Spiels frisch in {@code matches} (F9),
     * damit die Presence den aktuellen Stand aus der DB lesen kann. Nutzt
     * ausschließlich vorhandene Spalten (kein Schema-Eingriff) und berührt
     * {@code revealed}/{@code evaluated}/{@code channel}/{@code odds_*} nicht.
     */
    public void updateLiveScore(long id, Integer home, Integer away, MatchStatus status) {
        jdbc.sql("UPDATE matches SET home_score = :h, away_score = :a, status = :status WHERE id = :id")
                .param("h", home).param("a", away).param("status", status.name()).param("id", id)
                .update();
    }

    public void updateOdds(long id, java.math.BigDecimal home, java.math.BigDecimal draw, java.math.BigDecimal away) {
        jdbc.sql("UPDATE matches SET odds_home = :h, odds_draw = :d, odds_away = :a WHERE id = :id")
                .param("h", home).param("d", draw).param("a", away).param("id", id)
                .update();
    }

    public void upsert(Match m) {
        jdbc.sql("""
                        INSERT INTO matches
                            (id, home, away, kickoff, stage, group_label, channel, home_score, away_score, status, matchday)
                        VALUES
                            (:id, :home, :away, :kickoff, :stage, :groupLabel, :channel, :homeScore, :awayScore, :status, :matchday)
                        ON CONFLICT (id) DO UPDATE SET
                            home = EXCLUDED.home,
                            away = EXCLUDED.away,
                            kickoff = EXCLUDED.kickoff,
                            stage = EXCLUDED.stage,
                            group_label = EXCLUDED.group_label,
                            -- matchday nicht durch transiente nulls überschreiben.
                            matchday = COALESCE(EXCLUDED.matchday, matches.matchday),
                            channel = COALESCE(EXCLUDED.channel, matches.channel),
                            -- Einen bereits bekannten Stand NICHT durch einen transient
                            -- nullen Sync-Wert überschreiben (sonst löst der wiederkehrende
                            -- echte Stand eine Schein-Neubewertung aus, FR-017a).
                            home_score = COALESCE(EXCLUDED.home_score, matches.home_score),
                            away_score = COALESCE(EXCLUDED.away_score, matches.away_score),
                            -- Ein beendetes Spiel nicht durch Status-Geflacker der API
                            -- zurückstufen (verhindert erneutes Reveal/„kommend"/„live").
                            status = CASE
                                WHEN matches.status = 'FINISHED' AND EXCLUDED.status <> 'FINISHED'
                                THEN matches.status ELSE EXCLUDED.status END
                        """)
                .param("id", m.id())
                .param("home", m.home())
                .param("away", m.away())
                .param("kickoff", OffsetDateTime.ofInstant(m.kickoff(), ZoneOffset.UTC))
                .param("stage", m.stage().name())
                .param("groupLabel", m.groupLabel())
                .param("channel", m.channel())
                .param("homeScore", m.homeScore())
                .param("awayScore", m.awayScore())
                .param("status", m.status().name())
                .param("matchday", m.matchday())
                .update();
    }

    /**
     * Recap-Keys (F12), deren <b>alle</b> Spiele bereits {@code FINISHED} und
     * {@code evaluated} sind. Der Recap-Key gruppiert über das football-data-
     * {@code matchday}-Feld ({@code md:<n>}); fehlt es, dient die Turnier-Phase als
     * Fallback ({@code stage:<STAGE>}). Abgesagte Spiele bleiben unberücksichtigt.
     */
    public List<String> findCompletedRecapKeys() {
        return jdbc.sql("""
                        SELECT rk FROM (
                            SELECT COALESCE('md:' || matchday, 'stage:' || stage) AS rk,
                                   BOOL_AND(status = 'FINISHED' AND evaluated) AS complete
                            FROM matches
                            WHERE status <> 'CANCELLED'
                            GROUP BY COALESCE('md:' || matchday, 'stage:' || stage)
                        ) s
                        WHERE s.complete = TRUE
                        """)
                .query(String.class)
                .list();
    }

    public void markRevealed(long id) {
        jdbc.sql("UPDATE matches SET revealed = TRUE WHERE id = :id").param("id", id).update();
    }

    public void markEvaluated(long id) {
        jdbc.sql("UPDATE matches SET evaluated = TRUE WHERE id = :id").param("id", id).update();
    }

    /** Setzt {@code evaluated} zurück, damit ein korrigiertes Endergebnis neu bewertet wird (FR-017a). */
    public void markForReEvaluation(long id) {
        jdbc.sql("UPDATE matches SET evaluated = FALSE WHERE id = :id").param("id", id).update();
    }

    private static Match map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        OffsetDateTime kickoff = rs.getObject("kickoff", OffsetDateTime.class);
        Integer homeScore = (Integer) rs.getObject("home_score");
        Integer awayScore = (Integer) rs.getObject("away_score");
        return new Match(
                rs.getLong("id"),
                rs.getString("home"),
                rs.getString("away"),
                kickoff.toInstant(),
                Stage.valueOf(rs.getString("stage")),
                rs.getString("group_label"),
                rs.getString("channel"),
                rs.getBigDecimal("odds_home"),
                rs.getBigDecimal("odds_draw"),
                rs.getBigDecimal("odds_away"),
                homeScore,
                awayScore,
                MatchStatus.valueOf(rs.getString("status")),
                rs.getBoolean("revealed"),
                rs.getBoolean("evaluated"),
                (Integer) rs.getObject("matchday"));
    }
}
