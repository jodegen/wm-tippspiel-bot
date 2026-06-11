package com.example.wmtippspiel.reveal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.MatchStatus;
import com.example.wmtippspiel.domain.model.Stage;
import com.example.wmtippspiel.persistence.MatchRepository;
import com.example.wmtippspiel.persistence.TipRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Verfassung Prinzip III (NON-NEGOTIABLE): Pflicht-Tests des Reveal-Timings
 * (FR-011/012/013/031). Trigger an der Anpfiffzeit (UTC-Clock), Idempotenz,
 * keine Offenlegung abgesagter oder zukünftiger Spiele.
 */
class RevealServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-14T20:00:00Z");

    private MatchRepository matches;
    private TipRepository tips;
    private RevealPublisher publisher;
    private RevealService service;

    @BeforeEach
    void setUp() {
        matches = Mockito.mock(MatchRepository.class);
        tips = Mockito.mock(TipRepository.class);
        publisher = Mockito.mock(RevealPublisher.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new RevealService(matches, tips, publisher, clock);
        when(tips.findByMatch(anyLong())).thenReturn(List.of());
    }

    @Test
    @DisplayName("Legt nur angepfiffene, nicht abgesagte, noch nicht offengelegte Spiele offen")
    void revealsOnlyDueMatches() {
        Match due = match(1L, NOW.minusSeconds(60), MatchStatus.SCHEDULED, false);
        Match future = match(2L, NOW.plusSeconds(3600), MatchStatus.SCHEDULED, false);
        Match cancelled = match(3L, NOW.minusSeconds(60), MatchStatus.CANCELLED, false);
        when(matches.findUnrevealed()).thenReturn(List.of(due, future, cancelled));

        int revealed = service.revealDueMatches();

        assertThat(revealed).isEqualTo(1);
        verify(publisher).publishReveal(eq(due), Mockito.anyList());
        verify(publisher, never()).publishReveal(eq(future), Mockito.anyList());
        verify(publisher, never()).publishReveal(eq(cancelled), Mockito.anyList());
        verify(matches).markRevealed(1L);
        verify(matches, never()).markRevealed(2L);
        verify(matches, never()).markRevealed(3L);
    }

    @Test
    @DisplayName("Legt ein bereits offengelegtes Spiel nicht erneut offen (Idempotenz)")
    void doesNotRevealAlreadyRevealed() {
        Match alreadyRevealed = match(1L, NOW.minusSeconds(60), MatchStatus.IN_PLAY, true);
        when(matches.findUnrevealed()).thenReturn(List.of(alreadyRevealed));

        int revealed = service.revealDueMatches();

        assertThat(revealed).isZero();
        verify(publisher, never()).publishReveal(Mockito.any(), Mockito.anyList());
        verify(matches, never()).markRevealed(anyLong());
    }

    @Test
    @DisplayName("Nichts offenzulegen, wenn keine Kandidaten vorliegen")
    void noCandidatesNoReveal() {
        when(matches.findUnrevealed()).thenReturn(List.of());

        assertThat(service.revealDueMatches()).isZero();
        verify(publisher, never()).publishReveal(Mockito.any(), Mockito.anyList());
    }

    private static Match match(long id, Instant kickoff, MatchStatus status, boolean revealed) {
        return new Match(id, "Team A", "Team B", kickoff, Stage.GROUP_STAGE, "A", null,
                null, null, null, null, null, status, revealed, false);
    }
}
