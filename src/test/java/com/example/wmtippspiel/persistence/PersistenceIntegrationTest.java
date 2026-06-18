package com.example.wmtippspiel.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.example.wmtippspiel.domain.model.BotMessage;
import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.MatchStatus;
import com.example.wmtippspiel.domain.model.Stage;
import com.example.wmtippspiel.domain.model.Tip;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import liquibase.integration.spring.SpringLiquibase;

/**
 * Integrationstest der Foundational-Persistenz gegen ein echtes PostgreSQL
 * (Testcontainers). Verifiziert zugleich, dass die Liquibase-Changesets sauber
 * migrieren (Verfassung Prinzip II) und dass die {@link JdbcClient}-Repositories
 * inkl. der {@code ON CONFLICT}-Schutzregeln korrekt arbeiten (data-model.md).
 *
 * <p>Bewusst ohne Spring-Context (kein JDA/Discord-Token nötig): DataSource und
 * {@link JdbcClient} werden direkt verdrahtet.
 */
class PersistenceIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcClient jdbc;
    private static MatchRepository matches;
    private static TipRepository tips;
    private static BotMessageRepository botMessages;
    private static LeaderboardSnapshotRepository snapshots;
    private static MatchdayRecapRepository matchdayRecaps;

    private static final Instant KICKOFF = Instant.parse("2026-06-14T20:00:00Z");

    @BeforeAll
    static void startAndMigrate() throws Exception {
        POSTGRES.start();

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());

        // Liquibase wie in Produktion ausführen → verifiziert die Changesets.
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.afterPropertiesSet();

        jdbc = JdbcClient.create(dataSource);
        matches = new MatchRepository(jdbc);
        tips = new TipRepository(jdbc);
        botMessages = new BotMessageRepository(jdbc);
        snapshots = new LeaderboardSnapshotRepository(jdbc);
        matchdayRecaps = new MatchdayRecapRepository(jdbc);
    }

    @AfterAll
    static void stop() {
        POSTGRES.stop();
    }

    @BeforeEach
    void cleanTables() {
        jdbc.sql("TRUNCATE tips, matches, bot_messages, leaderboard_snapshot, matchday_recap CASCADE").update();
    }

    @Test
    @DisplayName("Liquibase legt alle drei Tabellen an")
    void migrationsCreateTables() {
        List<String> tables = jdbc.sql("""
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = 'public'
                        ORDER BY table_name
                        """)
                .query(String.class)
                .list();
        assertThat(tables).contains("matches", "tips", "bot_messages");
    }

    @Test
    @DisplayName("Match-Upsert aktualisiert Score/Status, schützt aber channel/revealed/evaluated")
    void upsertPreservesProtectedFields() {
        matches.upsert(scheduled(100L));

        // Manuell gepflegter Sender + bereits offengelegt/ausgewertet markiert.
        jdbc.sql("""
                        UPDATE matches SET channel = 'ARD', revealed = TRUE, evaluated = TRUE
                        WHERE id = 100
                        """).update();

        // Erneuter Sync (FINISHED, Endstand) – Mapping liefert keinen Sender (null).
        matches.upsert(new Match(100L, "Team A", "Team B", KICKOFF, Stage.GROUP_STAGE, "A",
                null, null, null, null, 2, 1, MatchStatus.FINISHED, false, false));

        Match stored = matches.findById(100L).orElseThrow();
        assertThat(stored.status()).isEqualTo(MatchStatus.FINISHED);
        assertThat(stored.homeScore()).isEqualTo(2);
        assertThat(stored.awayScore()).isEqualTo(1);
        // Geschützte Felder bleiben erhalten:
        assertThat(stored.channel()).isEqualTo("ARD");
        assertThat(stored.revealed()).isTrue();
        assertThat(stored.evaluated()).isTrue();
        // Kickoff round-trip (UTC):
        assertThat(stored.kickoff()).isEqualTo(KICKOFF);
    }

    @Test
    @DisplayName("Upsert überschreibt bekannten Endstand NICHT mit transientem null-Stand (Schein-Neubewertung)")
    void upsertDoesNotClobberKnownScoreWithNull() {
        // Beendetes, ausgewertetes Spiel mit Endstand 1:1.
        matches.upsert(new Match(200L, "Canada", "Bosnia-Herzegovina", KICKOFF, Stage.GROUP_STAGE, "A",
                null, null, null, null, 1, 1, MatchStatus.FINISHED, false, false));
        jdbc.sql("UPDATE matches SET evaluated = TRUE WHERE id = 200").update();

        // Transienter Sync: API liefert das Spiel kurzzeitig OHNE Stand (null/null).
        matches.upsert(new Match(200L, "Canada", "Bosnia-Herzegovina", KICKOFF, Stage.GROUP_STAGE, "A",
                null, null, null, null, null, null, MatchStatus.FINISHED, false, false));

        Match stored = matches.findById(200L).orElseThrow();
        assertThat(stored.homeScore()).isEqualTo(1); // 1:1 bleibt erhalten, nicht genullt
        assertThat(stored.awayScore()).isEqualTo(1);
    }

    @Test
    @DisplayName("Upsert stuft ein FINISHED-Spiel nicht durch Status-Geflacker zurück")
    void upsertDoesNotDowngradeFinishedStatus() {
        matches.upsert(new Match(201L, "Canada", "Bosnia-Herzegovina", KICKOFF, Stage.GROUP_STAGE, "A",
                null, null, null, null, 1, 1, MatchStatus.FINISHED, false, false));

        // Transienter Sync meldet das Spiel wieder als IN_PLAY.
        matches.upsert(new Match(201L, "Canada", "Bosnia-Herzegovina", KICKOFF, Stage.GROUP_STAGE, "A",
                null, null, null, null, 1, 1, MatchStatus.IN_PLAY, false, false));

        assertThat(matches.findById(201L).orElseThrow().status()).isEqualTo(MatchStatus.FINISHED);

        // Eine echte Endstand-Korrektur (nicht-null) wird weiterhin übernommen.
        matches.upsert(new Match(201L, "Canada", "Bosnia-Herzegovina", KICKOFF, Stage.GROUP_STAGE, "A",
                null, null, null, null, 2, 1, MatchStatus.FINISHED, false, false));
        Match corrected = matches.findById(201L).orElseThrow();
        assertThat(corrected.homeScore()).isEqualTo(2);
        assertThat(corrected.awayScore()).isEqualTo(1);
    }

    @Test
    @DisplayName("findUnrevealed schließt abgesagte und bereits offengelegte Spiele aus")
    void findUnrevealedFilters() {
        matches.upsert(scheduled(1L));
        matches.upsert(new Match(2L, "C", "D", KICKOFF, Stage.GROUP_STAGE, "A", null,
                null, null, null, null, null, MatchStatus.CANCELLED, false, false));
        matches.upsert(scheduled(3L));
        jdbc.sql("UPDATE matches SET revealed = TRUE WHERE id = 3").update();

        List<Long> ids = matches.findUnrevealed().stream().map(Match::id).toList();
        assertThat(ids).containsExactly(1L);
    }

    @Test
    @DisplayName("findUnevaluatedFinished liefert nur beendete, nicht ausgewertete Spiele")
    void findUnevaluatedFinishedFilters() {
        matches.upsert(new Match(1L, "A", "B", KICKOFF, Stage.GROUP_STAGE, "A", null,
                null, null, null, 1, 0, MatchStatus.FINISHED, true, false));
        matches.upsert(scheduled(2L)); // SCHEDULED
        Match finishedEvaluated = new Match(3L, "E", "F", KICKOFF, Stage.GROUP_STAGE, "A", null,
                null, null, null, 0, 0, MatchStatus.FINISHED, true, false);
        matches.upsert(finishedEvaluated);
        jdbc.sql("UPDATE matches SET evaluated = TRUE WHERE id = 3").update();

        List<Long> ids = matches.findUnevaluatedFinished().stream().map(Match::id).toList();
        assertThat(ids).containsExactly(1L);
    }

    @Test
    @DisplayName("findTippable liefert nur zukünftige, bekannte, nicht abgesagte Spiele, sortiert/limitiert")
    void findTippableFilters() {
        Instant now = KICKOFF;
        matches.upsert(new Match(1L, "A", "B", now.plusSeconds(7200), Stage.GROUP_STAGE, "A", null,
                null, null, null, null, null, MatchStatus.SCHEDULED, false, false));
        matches.upsert(new Match(2L, "C", "D", now.plusSeconds(3600), Stage.GROUP_STAGE, "A", null,
                null, null, null, null, null, MatchStatus.SCHEDULED, false, false));
        matches.upsert(new Match(3L, "TBD", "E", now.plusSeconds(1800), Stage.GROUP_STAGE, "A", null,
                null, null, null, null, null, MatchStatus.SCHEDULED, false, false)); // TBD → raus
        matches.upsert(new Match(4L, "F", "G", now.minusSeconds(60), Stage.GROUP_STAGE, "A", null,
                null, null, null, null, null, MatchStatus.SCHEDULED, false, false)); // Vergangenheit → raus

        List<Long> ids = matches.findTippable(now, 10).stream().map(Match::id).toList();
        assertThat(ids).containsExactly(2L, 1L); // nach kickoff aufsteigend
    }

    @Test
    @DisplayName("mark-Methoden setzen die jeweiligen Flags")
    void markFlags() {
        matches.upsert(scheduled(1L));
        matches.markRevealed(1L);
        assertThat(matches.findById(1L).orElseThrow().revealed()).isTrue();

        matches.markEvaluated(1L);
        assertThat(matches.findById(1L).orElseThrow().evaluated()).isTrue();

        matches.markForReEvaluation(1L);
        assertThat(matches.findById(1L).orElseThrow().evaluated()).isFalse();
    }

    @Test
    @DisplayName("Tip-Upsert hält einen Tipp pro User/Spiel und setzt vergebene Punkte nicht zurück")
    void tipUpsertKeepsSingleAndPreservesPoints() {
        matches.upsert(scheduled(50L));
        tips.upsert(new Tip("u1", 50L, "User1", 2, 1, KICKOFF.minusSeconds(3600), 0));
        tips.updatePoints("u1", 50L, 3); // ausgewertet

        // Erneute Abgabe (z. B. nochmal getippt) – darf nur einen Datensatz halten, points nicht zurücksetzen.
        tips.upsert(new Tip("u1", 50L, "User1", 0, 0, KICKOFF.minusSeconds(60), 0));

        List<Tip> matchTips = tips.findByMatch(50L);
        assertThat(matchTips).hasSize(1);
        Tip stored = matchTips.get(0);
        assertThat(stored.homeScore()).isEqualTo(0);
        assertThat(stored.awayScore()).isEqualTo(0);
        assertThat(stored.points()).isEqualTo(3); // bleibt erhalten
    }

    @Test
    @DisplayName("Leaderboard zählt exakte Treffer aus dem Score-Vergleich, NICHT aus dem Punktwert (FR-006/006a)")
    void leaderboardExactHitsDecoupledFromPoints() {
        // Zwei ausgewertete Spiele mit echtem Endstand.
        matches.upsert(finishedResult(1L, 2, 1));
        matches.upsert(finishedResult(2L, 0, 0));
        jdbc.sql("UPDATE matches SET evaluated = TRUE WHERE id IN (1, 2)").update();

        // User1: beide Tipps exakt — bewusst mit „falschen" Punktwerten gespeichert,
        // um zu beweisen, dass die Exakt-Zählung NICHT aus points abgeleitet wird:
        //   m1 exakt, aber points=0  → zählt trotzdem als Treffer
        //   m2 exakt, points=4
        tips.upsert(new Tip("u1", 1L, "User1", 2, 1, KICKOFF, 0));
        tips.updatePoints("u1", 1L, 0);
        tips.upsert(new Tip("u1", 2L, "User1", 0, 0, KICKOFF, 0));
        tips.updatePoints("u1", 2L, 4);
        // User2: nicht exakt (1:0 ≠ 2:1), aber points=4 gespeichert → darf NICHT als Treffer zählen.
        tips.upsert(new Tip("u2", 1L, "User2", 1, 0, KICKOFF, 0));
        tips.updatePoints("u2", 1L, 4);

        List<LeaderboardEntry> board = tips.leaderboard();

        assertThat(board).hasSize(2);
        // Punktgleichstand (je 4) → Tie-Breaker exakte Treffer entscheidet (u1 vor u2).
        assertThat(board.get(0).userId()).isEqualTo("u1");
        assertThat(board.get(0).totalPoints()).isEqualTo(4);
        assertThat(board.get(0).tipCount()).isEqualTo(2);
        assertThat(board.get(0).exactHits()).isEqualTo(2); // beide exakt, trotz points=0 bei m1
        assertThat(board.get(1).userId()).isEqualTo("u2");
        assertThat(board.get(1).totalPoints()).isEqualTo(4);
        assertThat(board.get(1).exactHits()).isZero(); // points=4, aber kein Score-Match
    }

    @Test
    @DisplayName("BotMessage-Upsert speichert und aktualisiert die getrackte Nachricht")
    void botMessageUpsertAndFind() {
        botMessages.upsert(new BotMessage("board:today", "chan1", "msg1", KICKOFF));
        Optional<BotMessage> found = botMessages.findByKey("board:today");
        assertThat(found).isPresent();
        assertThat(found.get().messageId()).isEqualTo("msg1");

        botMessages.upsert(new BotMessage("board:today", "chan1", "msg2", KICKOFF.plusSeconds(60)));
        assertThat(botMessages.findByKey("board:today").orElseThrow().messageId()).isEqualTo("msg2");
    }

    @Test
    @DisplayName("notified-score (F8): Default 0, Update und Lesen über echtes Schema (Changeset 008)")
    void notifiedScoreRoundTrip() {
        matches.upsert(scheduled(70L));
        assertThat(matches.getNotifiedScore(70L)).contains(new MatchRepository.NotifiedScore(0, 0));

        matches.updateNotifiedScore(70L, 2, 1);
        assertThat(matches.getNotifiedScore(70L)).contains(new MatchRepository.NotifiedScore(2, 1));

        assertThat(matches.getNotifiedScore(999L)).isEmpty();
    }

    @Test
    @DisplayName("F9: updateLiveScore hält Stand+Status frisch; findInPlay liefert nur laufende Spiele")
    void liveScoreAndInPlayRoundTrip() {
        matches.upsert(scheduled(80L)); // SCHEDULED
        assertThat(matches.findInPlay()).isEmpty();

        matches.updateLiveScore(80L, 1, 0, MatchStatus.IN_PLAY);
        List<Match> inPlay = matches.findInPlay();
        assertThat(inPlay).hasSize(1);
        Match live = inPlay.get(0);
        assertThat(live.id()).isEqualTo(80L);
        assertThat(live.homeScore()).isEqualTo(1);
        assertThat(live.awayScore()).isEqualTo(0);
        assertThat(live.status()).isEqualTo(MatchStatus.IN_PLAY);

        // IN_PLAY → FINISHED: fällt aus findInPlay heraus, Stand bleibt erhalten.
        matches.updateLiveScore(80L, 2, 1, MatchStatus.FINISHED);
        assertThat(matches.findInPlay()).isEmpty();
        assertThat(matches.findById(80L).orElseThrow().homeScore()).isEqualTo(2);

        // Schutzfelder unberührt.
        assertThat(matches.findById(80L).orElseThrow().revealed()).isFalse();
    }

    @Test
    @DisplayName("F11: leaderboard_snapshot wird vollständig ersetzt und übersteht (persistent) einen Neustart")
    void leaderboardSnapshotReplaceAndReload() {
        snapshots.replaceAll(java.util.Map.of("u1", 1, "u2", 2, "u3", 2), KICKOFF);
        assertThat(snapshots.findAllRanks())
                .containsEntry("u1", 1)
                .containsEntry("u2", 2)
                .containsEntry("u3", 2)
                .hasSize(3);

        // Neuer Batch ersetzt die Basis vollständig (verwaiste User verschwinden).
        snapshots.replaceAll(java.util.Map.of("u1", 2, "u2", 1), KICKOFF.plusSeconds(60));
        assertThat(snapshots.findAllRanks())
                .containsExactlyInAnyOrderEntriesOf(java.util.Map.of("u1", 2, "u2", 1));

        // Frisch instanziiertes Repository (≙ Neustart) liest denselben persistierten Stand.
        assertThat(new LeaderboardSnapshotRepository(jdbc).findAllRanks())
                .containsExactlyInAnyOrderEntriesOf(java.util.Map.of("u1", 2, "u2", 1));
    }

    @Test
    @DisplayName("F12: matchday round-trip; findCompletedRecapKeys nur bei vollständig FINISHED+evaluated")
    void matchdayPersistenceAndCompletedRecapKeys() {
        // Spieltag 1: zwei Spiele, beide FINISHED + evaluated → vollständig (md:1).
        matches.upsert(finishedMatchday(301L, 1, 2, 1));
        matches.upsert(finishedMatchday(302L, 1, 0, 0));
        jdbc.sql("UPDATE matches SET evaluated = TRUE WHERE id IN (301, 302)").update();
        // Spieltag 2: ein Spiel FINISHED+evaluated, eines noch nicht ausgewertet → unvollständig.
        matches.upsert(finishedMatchday(303L, 2, 3, 0));
        jdbc.sql("UPDATE matches SET evaluated = TRUE WHERE id = 303").update();
        matches.upsert(finishedMatchday(304L, 2, 1, 1)); // evaluated bleibt false

        assertThat(matches.findById(301L).orElseThrow().matchday()).isEqualTo(1);
        assertThat(matches.findCompletedRecapKeys()).containsExactly("md:1");
    }

    @Test
    @DisplayName("F12: matchdayLeaderboard summiert Punkte je Spieltag; tryClaim ist exakt einmalig")
    void matchdayLeaderboardAndIdempotentClaim() {
        matches.upsert(finishedMatchday(401L, 5, 2, 1));
        matches.upsert(finishedMatchday(402L, 5, 0, 0));
        jdbc.sql("UPDATE matches SET evaluated = TRUE WHERE id IN (401, 402)").update();
        tips.upsert(new Tip("u1", 401L, "Alice", 2, 1, KICKOFF, 0));
        tips.updatePoints("u1", 401L, 4);
        tips.upsert(new Tip("u1", 402L, "Alice", 1, 0, KICKOFF, 0));
        tips.updatePoints("u1", 402L, 2);
        tips.upsert(new Tip("u2", 401L, "Bob", 0, 3, KICKOFF, 0));
        tips.updatePoints("u2", 401L, 0);

        List<MatchdayScore> board = tips.matchdayLeaderboard("md:5");
        assertThat(board).extracting(MatchdayScore::username).containsExactly("Alice", "Bob");
        assertThat(board.get(0).points()).isEqualTo(6);
        assertThat(board.get(1).points()).isZero();

        // Idempotenz: erster Claim true, jeder weitere false (auch nach „Neustart").
        assertThat(matchdayRecaps.tryClaim("md:5", KICKOFF)).isTrue();
        assertThat(matchdayRecaps.tryClaim("md:5", KICKOFF.plusSeconds(60))).isFalse();
        assertThat(new MatchdayRecapRepository(jdbc).tryClaim("md:5", KICKOFF.plusSeconds(120))).isFalse();
    }

    private static Match scheduled(long id) {
        return new Match(id, "Team A", "Team B", KICKOFF, Stage.GROUP_STAGE, "A", null,
                null, null, null, null, null, MatchStatus.SCHEDULED, false, false);
    }

    private static Match finishedResult(long id, int home, int away) {
        return new Match(id, "Team A", "Team B", KICKOFF, Stage.GROUP_STAGE, "A", null,
                null, null, null, home, away, MatchStatus.FINISHED, false, false);
    }

    private static Match finishedMatchday(long id, int matchday, int home, int away) {
        return new Match(id, "Team A", "Team B", KICKOFF, Stage.GROUP_STAGE, "A", null,
                null, null, null, home, away, MatchStatus.FINISHED, false, false, matchday);
    }
}
