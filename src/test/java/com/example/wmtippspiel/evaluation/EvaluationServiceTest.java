package com.example.wmtippspiel.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
import com.example.wmtippspiel.persistence.MatchRepository;
import com.example.wmtippspiel.persistence.TipRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Verfassung Prinzip III (NON-NEGOTIABLE): Pflicht-Tests der Auswertung
 * (FR-014/015/016) und der Neubewertung nach Endstand-Korrektur (FR-017a).
 */
class EvaluationServiceTest {

    private static final Instant KICKOFF = Instant.parse("2026-06-14T20:00:00Z");

    private MatchRepository matches;
    private TipRepository tips;
    private EvaluationPublisher publisher;
    private EvaluationService service;

    @BeforeEach
    void setUp() {
        matches = Mockito.mock(MatchRepository.class);
        tips = Mockito.mock(TipRepository.class);
        publisher = Mockito.mock(EvaluationPublisher.class);
        service = new EvaluationService(matches, tips, new ScoringService(), publisher);
    }

    @Test
    @DisplayName("Wertet ein beendetes Spiel mit korrekten 4/3/2/0-Punkten aus")
    void evaluatesFinishedMatch() {
        Match finished = finished(10L, 2, 1);
        when(matches.findUnevaluatedFinished()).thenReturn(List.of(finished));
        when(tips.findByMatch(10L)).thenReturn(List.of(
                tip("a", 10L, 2, 1),   // exakt → 4
                tip("b", 10L, 3, 0),   // Tendenz Heimsieg, Differenz ≠ → 2
                tip("c", 10L, 1, 2))); // falsche Tendenz → 0

        int count = service.evaluateFinishedMatches();

        assertThat(count).isEqualTo(1);
        verify(tips).updatePoints("a", 10L, 4);
        verify(tips).updatePoints("b", 10L, 2);
        verify(tips).updatePoints("c", 10L, 0);
        verify(matches).markEvaluated(10L);
        verify(publisher).publishEvaluation(eq(finished), Mockito.anyList(), eq(false));
    }

    @Test
    @DisplayName("Wertet ein bereits ausgewertetes Spiel nicht erneut aus (Idempotenz)")
    void idempotentWhenNothingDue() {
        when(matches.findUnevaluatedFinished()).thenReturn(List.of());

        assertThat(service.evaluateFinishedMatches()).isZero();
        verify(tips, never()).updatePoints(Mockito.anyString(), anyLong(), Mockito.anyInt());
        verify(publisher, never()).publishEvaluation(Mockito.any(), Mockito.anyList(), Mockito.anyBoolean());
    }

    @Test
    @DisplayName("Neubewertung bei korrigiertem Endstand rechnet neu und markiert als Korrektur (FR-017a)")
    void reevaluatesOnCorrection() {
        Match corrected = finished(10L, 2, 2); // Endstand korrigiert auf 2:2
        when(tips.findByMatch(10L)).thenReturn(List.of(
                tip("a", 10L, 2, 1),   // vorher exakt, jetzt falsche Tendenz → 0
                tip("b", 10L, 0, 0))); // Remis, falsche Höhe (Differenz 0) → 3

        service.reevaluate(corrected);

        verify(tips).updatePoints("a", 10L, 0);
        verify(tips).updatePoints("b", 10L, 3);
        verify(matches).markEvaluated(10L);
        verify(publisher).publishEvaluation(eq(corrected), Mockito.anyList(), eq(true));
    }

    private static Match finished(long id, int home, int away) {
        return new Match(id, "Team A", "Team B", KICKOFF, Stage.GROUP_STAGE, "A", null,
                null, null, null, home, away, MatchStatus.FINISHED, true, false);
    }

    private static Tip tip(String user, long matchId, int home, int away) {
        return new Tip(user, matchId, user.toUpperCase(), home, away, KICKOFF.minusSeconds(3600), 0);
    }
}
