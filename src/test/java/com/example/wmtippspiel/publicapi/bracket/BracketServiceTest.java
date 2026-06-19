package com.example.wmtippspiel.publicapi.bracket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.MatchStatus;
import com.example.wmtippspiel.domain.model.MatchWinner;
import com.example.wmtippspiel.domain.model.Stage;
import com.example.wmtippspiel.persistence.MatchRepository;
import com.example.wmtippspiel.publicapi.dto.BracketDto;
import com.example.wmtippspiel.publicapi.dto.BracketMatchDto;
import com.example.wmtippspiel.publicapi.dto.BracketParticipantDto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Tests des Bracket-Builders (Feature 010): Struktur, Platzhalter (US2), Gewinner-Fortschritt (US3). */
class BracketServiceTest {

    private MatchRepository matches;
    private BracketService service;

    @BeforeEach
    void setUp() {
        matches = Mockito.mock(MatchRepository.class);
        service = new BracketService(matches);
    }

    private void knockout(Match... ms) {
        when(matches.findKnockout()).thenReturn(List.of(ms));
    }

    private static Match scheduled(long id, Stage stage, Instant kickoff, String home, String away) {
        return new Match(id, home, away, kickoff, stage, null, null, null, null, null,
                null, null, MatchStatus.SCHEDULED, false, false);
    }

    private static Match finished(long id, Stage stage, Instant kickoff, String home, String away,
                                  int hs, int as, MatchWinner winner) {
        return new Match(id, home, away, kickoff, stage, null, null, null, null, null,
                hs, as, MatchStatus.FINISHED, false, false, null, winner);
    }

    private static BracketMatchDto match(BracketDto bracket, int fifaNo) {
        return bracket.rounds().stream()
                .flatMap(r -> r.matches().stream())
                .filter(m -> m.fifaMatchNo() == fifaNo)
                .findFirst()
                .orElseThrow();
    }

    // ---- US1 / US2: Struktur & Platzhalter ----

    @Test
    @DisplayName("Ohne K.o.-Spiele: vollständige 6-Runden-Struktur 16/8/4/2/1/1")
    void fullStructureWhenEmpty() {
        knockout();
        BracketDto bracket = service.build();

        assertThat(bracket.rounds()).hasSize(6);
        assertThat(bracket.rounds().get(0).stage()).isEqualTo("LAST_32");
        assertThat(bracket.rounds().get(0).matches()).hasSize(16);
        assertThat(bracket.rounds().get(1).matches()).hasSize(8);
        assertThat(bracket.rounds().get(2).matches()).hasSize(4);
        assertThat(bracket.rounds().get(3).matches()).hasSize(2);
        assertThat(bracket.rounds().get(4).stage()).isEqualTo("THIRD_PLACE");
        assertThat(bracket.rounds().get(4).matches()).hasSize(1);
        assertThat(bracket.rounds().get(5).stage()).isEqualTo("FINAL");
        assertThat(bracket.rounds().get(5).matches()).hasSize(1);
    }

    @Test
    @DisplayName("Ohne Daten trägt jede Position ein Platzhalter-Label; nie leer/null (SC-005)")
    void everyParticipantHasPlaceholder() {
        knockout();
        BracketDto bracket = service.build();

        bracket.rounds().stream().flatMap(r -> r.matches().stream()).forEach(m -> {
            assertExactlyOne(m.home());
            assertExactlyOne(m.away());
            assertThat(m.home().teamName()).isNull();
            assertThat(m.away().teamName()).isNull();
        });
    }

    @Test
    @DisplayName("LAST_32-Platzhalter exakt nach Topologie; höhere Runden generisch 'Sieger Match X'")
    void placeholderLabels() {
        knockout();
        BracketDto bracket = service.build();

        BracketMatchDto m73 = match(bracket, 73);
        assertThat(m73.home().placeholder()).isEqualTo("Sieger Gruppe A");
        assertThat(m73.away().placeholder()).isEqualTo("Zweiter Gruppe B");

        BracketMatchDto m89 = match(bracket, 89); // sources [74, 77]
        assertThat(m89.home().placeholder()).isEqualTo("Sieger Match 74");
        assertThat(m89.away().placeholder()).isEqualTo("Sieger Match 77");

        BracketMatchDto m103 = match(bracket, 103); // Verlierer der Halbfinals
        assertThat(m103.home().placeholder()).isEqualTo("Verlierer Match 101");
        assertThat(m103.away().placeholder()).isEqualTo("Verlierer Match 102");
    }

    // ---- US3: Gewinner-Ableitung (winnerOf) ----

    @Test
    @DisplayName("winnerOf: eindeutige Tordifferenz entscheidet")
    void winnerByGoalDifference() {
        Match m = finished(1, Stage.LAST_32, Instant.parse("2026-06-29T16:00:00Z"), "Brazil", "Mexico", 2, 1, null);
        assertThat(BracketService.winnerOf(m)).isEqualTo(MatchWinner.HOME_TEAM);
    }

    @Test
    @DisplayName("winnerOf: 1:1 mit winner=HOME_TEAM (Elfmeter) → HOME_TEAM")
    void winnerByColumnOnDrawScore() {
        Match m = finished(1, Stage.LAST_32, Instant.parse("2026-06-29T16:00:00Z"), "Spain", "Italy", 1, 1, MatchWinner.HOME_TEAM);
        assertThat(BracketService.winnerOf(m)).isEqualTo(MatchWinner.HOME_TEAM);
    }

    @Test
    @DisplayName("winnerOf: Remis ohne Sieger-Info → null (kein Nachrücken)")
    void noWinnerOnUndecidedDraw() {
        Match m = finished(1, Stage.LAST_32, Instant.parse("2026-06-29T16:00:00Z"), "A", "B", 1, 1, null);
        assertThat(BracketService.winnerOf(m)).isNull();
    }

    @Test
    @DisplayName("winnerOf: nicht beendetes Spiel → null")
    void noWinnerWhenNotFinished() {
        Match m = scheduled(1, Stage.LAST_32, Instant.parse("2026-06-29T16:00:00Z"), "A", "B");
        assertThat(BracketService.winnerOf(m)).isNull();
    }

    // ---- US3: Fortschritt im Baum ----

    @Test
    @DisplayName("Sieger eines LAST_32-Spiels rückt ins Folge-Spiel; eigener DTO-winner gesetzt")
    void winnerAdvancesToNextMatch() {
        // einziges LAST_32-Spiel → Slot 1 → FIFA 73; nextMatchNo = 90 (home-Quelle)
        knockout(finished(700, Stage.LAST_32, Instant.parse("2026-06-29T16:00:00Z"), "Brazil", "Mexico", 3, 0, null));
        BracketDto bracket = service.build();

        assertThat(match(bracket, 73).winner()).isEqualTo("HOME_TEAM");
        assertThat(match(bracket, 90).home()).isEqualTo(BracketParticipantDto.team("Brazil"));
        assertThat(match(bracket, 90).away().placeholder()).isEqualTo("Sieger Match 75"); // andere Quelle offen
    }

    @Test
    @DisplayName("Laufendes Spiel: kein vorzeitiges Nachrücken (Folge-Slot bleibt Platzhalter)")
    void inPlayDoesNotAdvance() {
        Match inPlay = new Match(700, "Brazil", "Mexico", Instant.parse("2026-06-29T16:00:00Z"),
                Stage.LAST_32, null, null, null, null, null, 1, 0, MatchStatus.IN_PLAY, false, false);
        knockout(inPlay);
        BracketDto bracket = service.build();

        assertThat(match(bracket, 90).home().placeholder()).isEqualTo("Sieger Match 73");
        assertThat(match(bracket, 73).winner()).isNull();
    }

    @Test
    @DisplayName("Spiel um Platz 3 erhält die beiden Halbfinal-Verlierer; Finale die Sieger")
    void thirdPlaceGetsSemifinalLosers() {
        Match sf1 = finished(800, Stage.SEMI_FINAL, Instant.parse("2026-07-14T19:00:00Z"), "Alpha", "Beta", 2, 1, null);
        Match sf2 = finished(801, Stage.SEMI_FINAL, Instant.parse("2026-07-15T19:00:00Z"), "Gamma", "Delta", 0, 1, null);
        knockout(sf1, sf2); // Slot1→101, Slot2→102
        BracketDto bracket = service.build();

        BracketMatchDto third = match(bracket, 103);
        assertThat(third.home()).isEqualTo(BracketParticipantDto.team("Beta"));   // Verlierer 101
        assertThat(third.away()).isEqualTo(BracketParticipantDto.team("Gamma"));  // Verlierer 102

        BracketMatchDto fin = match(bracket, 104);
        assertThat(fin.home()).isEqualTo(BracketParticipantDto.team("Alpha"));    // Sieger 101
        assertThat(fin.away()).isEqualTo(BracketParticipantDto.team("Delta"));    // Sieger 102
    }

    private static void assertExactlyOne(BracketParticipantDto p) {
        boolean hasTeam = p.teamName() != null;
        boolean hasPlaceholder = p.placeholder() != null;
        assertThat(hasTeam ^ hasPlaceholder).as("genau eines von teamName/placeholder").isTrue();
    }
}
