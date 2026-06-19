package com.example.wmtippspiel.publicapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.MatchStatus;
import com.example.wmtippspiel.domain.model.Stage;
import com.example.wmtippspiel.domain.model.Tip;
import com.example.wmtippspiel.persistence.LeaderboardSnapshotRepository;
import com.example.wmtippspiel.persistence.MatchRepository;
import com.example.wmtippspiel.persistence.TipRepository;
import com.example.wmtippspiel.publicapi.dto.MatchTipsDto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Kernlogik-Test (Verfassung Prinzip III — Reveal-Timing, test-first) für das
 * serverseitige Anpfiff-Gate des Tipps-Endpoints. Konservative Regel:
 * Tipps nur, wenn {@code now() (UTC) ≥ kickoff} UND {@code revealed} (FR-012/013,
 * Clarify Q2). Reiner Unit-Test mit fixer {@link Clock} und gemockten Repos.
 */
class RevealGateTest {

    private static final Instant NOW = Instant.parse("2026-06-20T20:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final long MATCH_ID = 7L;

    private final MatchRepository matches = mock(MatchRepository.class);
    private final TipRepository tips = mock(TipRepository.class);
    private final LeaderboardSnapshotRepository snapshots = mock(LeaderboardSnapshotRepository.class);
    private final PublicQueryService service = new PublicQueryService(matches, tips, snapshots, CLOCK);

    private static Match match(Instant kickoff, boolean revealed, boolean evaluated, MatchStatus status) {
        return new Match(MATCH_ID, "Deutschland", "Frankreich", kickoff, Stage.GROUP_STAGE, "A", "ARD",
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, null, null, status, revealed, evaluated, 1);
    }

    private static Tip tip() {
        return new Tip("discord-1", MATCH_ID, "Alice", 2, 1, Instant.parse("2026-06-20T18:00:00Z"), 0);
    }

    @Test
    @DisplayName("Vor Anpfiff (now < kickoff): released=false, keine Tipps geladen")
    void beforeKickoff() {
        when(matches.findById(MATCH_ID)).thenReturn(Optional.of(
                match(NOW.plusSeconds(3600), true, false, MatchStatus.SCHEDULED)));
        MatchTipsDto result = service.matchTips(MATCH_ID);
        assertThat(result.released()).isFalse();
        assertThat(result.tips()).isEmpty();
        verify(tips, never()).findByMatch(MATCH_ID);
    }

    @Test
    @DisplayName("Anpfiff erreicht, aber nicht revealed: released=false, keine Tipps")
    void kickoffReachedButNotRevealed() {
        when(matches.findById(MATCH_ID)).thenReturn(Optional.of(
                match(NOW.minusSeconds(60), false, false, MatchStatus.IN_PLAY)));
        MatchTipsDto result = service.matchTips(MATCH_ID);
        assertThat(result.released()).isFalse();
        assertThat(result.tips()).isEmpty();
        verify(tips, never()).findByMatch(MATCH_ID);
    }

    @Test
    @DisplayName("Anpfiff erreicht UND revealed: Tipps werden ausgeliefert")
    void releasedWhenKickoffAndRevealed() {
        when(matches.findById(MATCH_ID)).thenReturn(Optional.of(
                match(NOW.minusSeconds(60), true, false, MatchStatus.IN_PLAY)));
        when(tips.findByMatch(MATCH_ID)).thenReturn(List.of(tip()));
        MatchTipsDto result = service.matchTips(MATCH_ID);
        assertThat(result.released()).isTrue();
        assertThat(result.tips()).hasSize(1);
        assertThat(result.tips().get(0).displayName()).isEqualTo("Alice");
        // nicht gewertetes Spiel ⇒ keine Punkte ausgewiesen
        assertThat(result.tips().get(0).points()).isNull();
    }

    @Test
    @DisplayName("Gewertetes Spiel: Punkte werden mitgeliefert")
    void pointsWhenEvaluated() {
        when(matches.findById(MATCH_ID)).thenReturn(Optional.of(
                match(NOW.minusSeconds(7200), true, true, MatchStatus.FINISHED)));
        when(tips.findByMatch(MATCH_ID)).thenReturn(List.of(tip()));
        assertThat(service.matchTips(MATCH_ID).tips().get(0).points()).isZero();
    }

    @Test
    @DisplayName("Unbekanntes Spiel: 404-Signal ohne Datenleck")
    void unknownMatch() {
        when(matches.findById(MATCH_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.matchTips(MATCH_ID))
                .isInstanceOf(PublicNotFoundException.class);
    }
}
