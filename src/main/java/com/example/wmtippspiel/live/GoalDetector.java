package com.example.wmtippspiel.live;

import java.util.ArrayList;
import java.util.List;

import com.example.wmtippspiel.domain.model.Match;

import org.springframework.stereotype.Component;

/**
 * Reine Diff-Logik (F8): vergleicht den zuletzt gemeldeten Stand mit dem
 * aktuellen Stand und erzeugt Tor-/Korrektur-Ereignisse. Persistiert selbst
 * nichts (das übernimmt der Aufrufer).
 *
 * <ul>
 *   <li>Gleichstand → keine Events (idempotent, FR-007)</li>
 *   <li>Anstieg → je zusätzlichem Tor ein GOAL mit laufendem Stand (FR-006/014)</li>
 *   <li>Stand sinkt (VAR) → ein CORRECTION, kein GOAL (FR-008)</li>
 * </ul>
 *
 * Recovery/Nachmelden (FR-009a) ergibt sich daraus, dass der gemeldete Stand
 * persistent ist: nach einem Neustart wird die Differenz gegen den gespeicherten
 * Stand gebildet.
 */
@Component
public class GoalDetector {

    public List<GoalEvent> detect(int notifiedHome, int notifiedAway,
                                  int currentHome, int currentAway, Match match) {
        if (currentHome == notifiedHome && currentAway == notifiedAway) {
            return List.of();
        }
        // Mindestens ein Wert kleiner → Abwärtskorrektur (VAR): keine Tore.
        if (currentHome < notifiedHome || currentAway < notifiedAway) {
            return List.of(GoalEvent.correction(match, currentHome, currentAway));
        }
        // Anstieg: je zusätzlichem Tor ein Event mit laufendem Stand.
        List<GoalEvent> events = new ArrayList<>();
        int home = notifiedHome;
        int away = notifiedAway;
        while (home < currentHome) {
            home++;
            events.add(GoalEvent.goal(match, GoalEvent.ScoringTeam.HOME, home, away, null));
        }
        while (away < currentAway) {
            away++;
            events.add(GoalEvent.goal(match, GoalEvent.ScoringTeam.AWAY, home, away, null));
        }
        return events;
    }
}
