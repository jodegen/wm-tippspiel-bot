package com.example.wmtippspiel.live;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.MatchStatus;
import com.example.wmtippspiel.domain.model.Stage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests der reinen Tor-Erkennung (F8): Anstieg, Mehrfach-Tore, Idempotenz, VAR, Recovery. */
class GoalDetectorTest {

    private final GoalDetector detector = new GoalDetector();
    private final Match match = new Match(1L, "Deutschland", "Curaçao",
            Instant.parse("2026-06-14T19:00:00Z"), Stage.GROUP_STAGE, "A", null,
            null, null, null, null, null, MatchStatus.IN_PLAY, true, false);

    @Test
    @DisplayName("Ein Heimtor 0:0 → 1:0 erzeugt genau ein GOAL-Event (Heim)")
    void singleHomeGoal() {
        List<GoalEvent> events = detector.detect(0, 0, 1, 0, match);
        assertThat(events).hasSize(1);
        GoalEvent e = events.get(0);
        assertThat(e.kind()).isEqualTo(GoalEvent.Kind.GOAL);
        assertThat(e.scoringTeam()).isEqualTo(GoalEvent.ScoringTeam.HOME);
        assertThat(e.newHome()).isEqualTo(1);
        assertThat(e.newAway()).isZero();
    }

    @Test
    @DisplayName("Mehrere Tore zwischen zwei Polls → mehrere Events mit laufendem Stand")
    void multipleGoalsRunningScore() {
        List<GoalEvent> events = detector.detect(1, 0, 3, 1, match); // +2 Heim, +1 Gast

        assertThat(events).hasSize(3);
        assertThat(events).allMatch(e -> e.kind() == GoalEvent.Kind.GOAL);
        // Laufender Stand: 2:0, 3:0, dann 3:1
        assertThat(events.get(0).scoringTeam()).isEqualTo(GoalEvent.ScoringTeam.HOME);
        assertThat(events.get(0).newHome()).isEqualTo(2);
        assertThat(events.get(0).newAway()).isZero();
        assertThat(events.get(1).newHome()).isEqualTo(3);
        assertThat(events.get(1).newAway()).isZero();
        assertThat(events.get(2).scoringTeam()).isEqualTo(GoalEvent.ScoringTeam.AWAY);
        assertThat(events.get(2).newHome()).isEqualTo(3);
        assertThat(events.get(2).newAway()).isEqualTo(1);
    }

    @Test
    @DisplayName("Unveränderter Stand erzeugt keine Events (idempotent)")
    void idempotentOnUnchanged() {
        assertThat(detector.detect(2, 1, 2, 1, match)).isEmpty();
    }

    @Test
    @DisplayName("Stand sinkt (VAR) → genau ein CORRECTION-Event, kein GOAL")
    void varDowngradeProducesCorrection() {
        List<GoalEvent> events = detector.detect(1, 0, 0, 0, match);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).kind()).isEqualTo(GoalEvent.Kind.CORRECTION);
        assertThat(events.get(0).newHome()).isZero();
        assertThat(events.get(0).newAway()).isZero();
        assertThat(events).noneMatch(e -> e.kind() == GoalEvent.Kind.GOAL);
    }

    @Test
    @DisplayName("Teilweise Korrektur nach unten (2:1 → 2:0) → CORRECTION")
    void partialDowngradeProducesCorrection() {
        List<GoalEvent> events = detector.detect(2, 1, 2, 0, match);
        assertThat(events).singleElement()
                .matches(e -> e.kind() == GoalEvent.Kind.CORRECTION && e.newAway() == 0);
    }

    @Test
    @DisplayName("Recovery/Nachmelden: gegen persistierten Stand 1:0 wird das verpasste 2:0 nachgereicht")
    void recoveryReportsMissedGoal() {
        // notified = 1:0 (vor Neustart persistiert), aktuell 2:0
        List<GoalEvent> events = detector.detect(1, 0, 2, 0, match);
        assertThat(events).singleElement()
                .matches(e -> e.kind() == GoalEvent.Kind.GOAL
                        && e.scoringTeam() == GoalEvent.ScoringTeam.HOME
                        && e.newHome() == 2);
    }
}
