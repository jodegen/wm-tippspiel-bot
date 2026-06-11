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
    }

    @AfterAll
    static void stop() {
        POSTGRES.stop();
    }

    @BeforeEach
    void cleanTables() {
        jdbc.sql("TRUNCATE tips, matches, bot_messages CASCADE").update();
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
    @DisplayName("Leaderboard aggregiert Punkte, Tipps und exakte Treffer und sortiert korrekt")
    void leaderboardAggregatesAndSorts() {
        matches.upsert(scheduled(1L));
        matches.upsert(scheduled(2L));
        // User1: 3 + 1 = 4 Punkte, 2 Tipps, 1 exakter Treffer
        tips.upsert(new Tip("u1", 1L, "User1", 2, 1, KICKOFF, 0));
        tips.updatePoints("u1", 1L, 3);
        tips.upsert(new Tip("u1", 2L, "User1", 1, 0, KICKOFF, 0));
        tips.updatePoints("u1", 2L, 1);
        // User2: 3 Punkte, 1 Tipp, 1 exakter Treffer
        tips.upsert(new Tip("u2", 1L, "User2", 0, 0, KICKOFF, 0));
        tips.updatePoints("u2", 1L, 3);

        List<LeaderboardEntry> board = tips.leaderboard();

        assertThat(board).hasSize(2);
        assertThat(board.get(0).userId()).isEqualTo("u1");
        assertThat(board.get(0).totalPoints()).isEqualTo(4);
        assertThat(board.get(0).tipCount()).isEqualTo(2);
        assertThat(board.get(0).exactHits()).isEqualTo(1);
        assertThat(board.get(1).userId()).isEqualTo("u2");
        assertThat(board.get(1).totalPoints()).isEqualTo(3);
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

    private static Match scheduled(long id) {
        return new Match(id, "Team A", "Team B", KICKOFF, Stage.GROUP_STAGE, "A", null,
                null, null, null, null, null, MatchStatus.SCHEDULED, false, false);
    }
}
