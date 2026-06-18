package com.example.wmtippspiel.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.MatchStatus;
import com.example.wmtippspiel.domain.model.Stage;
import com.example.wmtippspiel.domain.model.Tip;
import com.example.wmtippspiel.domain.scoring.ScoringService;
import com.example.wmtippspiel.evaluation.RecalculationService.RecalcSummary;
import com.example.wmtippspiel.persistence.MatchRepository;
import com.example.wmtippspiel.persistence.TipRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Verfassung Prinzip III (NON-NEGOTIABLE): Pflicht-Tests der rückwirkenden
 * Punkte-Neuberechnung (FR-008/009/010/011). Nutzt die echte {@link ScoringService}
 * als einzige Berechnungsquelle.
 */
class RecalculationServiceTest {

    private static final Instant KICKOFF = Instant.parse("2026-06-14T20:00:00Z");

    private MatchRepository matches;
    private TipRepository tips;
    private RecalculationService service;

    @BeforeEach
    void setUp() {
        matches = Mockito.mock(MatchRepository.class);
        tips = Mockito.mock(TipRepository.class);
        service = new RecalculationService(matches, tips, new ScoringService());
    }

    @Test
    @DisplayName("Schreibt nur bei Abweichung neu (Altwert 3 → 4 bei exaktem Tipp)")
    void rewritesOnlyWhenDifferent() {
        Match finished = evaluated(10L, 2, 1);
        when(matches.findEvaluated()).thenReturn(List.of(finished));
        when(tips.findByMatch(10L)).thenReturn(List.of(
                tip("a", 10L, 2, 1, 3),   // exakt: Altschema 3 → neu 4 ⇒ Update
                tip("b", 10L, 2, 1, 4))); // exakt: bereits 4 ⇒ kein Update (Idempotenz)

        RecalcSummary summary = service.recalculateAll();

        verify(tips).updatePoints("a", 10L, 4);
        verify(tips, never()).updatePoints(Mockito.eq("b"), anyLong(), Mockito.anyInt());
        assertThat(summary.matchesScanned()).isEqualTo(1);
        assertThat(summary.tipsScanned()).isEqualTo(2);
        assertThat(summary.tipsChanged()).isEqualTo(1);
    }

    @Test
    @DisplayName("Altwert 1 wird zu 3 (richtige Differenz) bzw. 2 (nur Tendenz)")
    void mapsOldTendencyToNewTiers() {
        Match finished = evaluated(10L, 4, 1); // Differenz +3
        when(matches.findEvaluated()).thenReturn(List.of(finished));
        when(tips.findByMatch(10L)).thenReturn(List.of(
                tip("c", 10L, 3, 0, 1),   // Differenz +3 ⇒ 3
                tip("d", 10L, 2, 1, 1))); // Heimsieg, Differenz +1 ≠ +3 ⇒ 2

        service.recalculateAll();

        verify(tips).updatePoints("c", 10L, 3);
        verify(tips).updatePoints("d", 10L, 2);
    }

    @Test
    @DisplayName("Zweiter Lauf direkt nach dem ersten ändert nichts (Idempotenz)")
    void secondRunIsNoOp() {
        Match finished = evaluated(10L, 2, 1);
        when(matches.findEvaluated()).thenReturn(List.of(finished));
        // Werte stehen bereits korrekt nach neuem Schema.
        when(tips.findByMatch(10L)).thenReturn(List.of(
                tip("a", 10L, 2, 1, 4),   // exakt → 4
                tip("b", 10L, 3, 0, 2))); // Tendenz Heimsieg, Differenz ≠ → 2

        RecalcSummary summary = service.recalculateAll();

        verify(tips, never()).updatePoints(anyString(), anyLong(), Mockito.anyInt());
        assertThat(summary.tipsChanged()).isZero();
    }

    @Test
    @DisplayName("Nur ausgewertete Spiele werden geladen; nicht-evaluierte bleiben unangetastet (FR-010)")
    void touchesOnlyEvaluatedMatches() {
        // findEvaluated() liefert per Definition keine nicht-evaluierten Spiele.
        when(matches.findEvaluated()).thenReturn(List.of());

        RecalcSummary summary = service.recalculateAll();

        verify(matches).findEvaluated();
        verify(tips, never()).findByMatch(anyLong());
        verify(tips, never()).updatePoints(anyString(), anyLong(), Mockito.anyInt());
        assertThat(summary.matchesScanned()).isZero();
        assertThat(summary.tipsChanged()).isZero();
    }

    @Test
    @DisplayName("Spiel ohne Endstand wird übersprungen")
    void skipsMatchWithoutResult() {
        Match noResult = new Match(10L, "A", "B", KICKOFF, Stage.GROUP_STAGE, "A", null,
                null, null, null, null, null, MatchStatus.FINISHED, true, true);
        when(matches.findEvaluated()).thenReturn(List.of(noResult));

        RecalcSummary summary = service.recalculateAll();

        verify(tips, never()).findByMatch(anyLong());
        assertThat(summary.matchesScanned()).isZero();
    }

    private static Match evaluated(long id, int home, int away) {
        return new Match(id, "Team A", "Team B", KICKOFF, Stage.GROUP_STAGE, "A", null,
                null, null, null, home, away, MatchStatus.FINISHED, true, true);
    }

    private static Tip tip(String user, long matchId, int home, int away, int points) {
        return new Tip(user, matchId, user.toUpperCase(), home, away, KICKOFF.minusSeconds(3600), points);
    }
}
