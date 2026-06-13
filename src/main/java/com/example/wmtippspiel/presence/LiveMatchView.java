package com.example.wmtippspiel.presence;

import java.time.Instant;

/**
 * Sicht auf ein laufendes Spiel für die Presence-Auswahl (F9). Entkoppelt den
 * reinen {@link PresenceStateResolver} vom Domänen-{@code Match} und trägt die
 * für FR-013 nötige {@code lastChange}-Information.
 *
 * @param matchId   Spiel-ID
 * @param home      Heim-Teamname (Klartext, wird via TeamCodeResolver gekürzt)
 * @param away      Gast-Teamname
 * @param homeScore aktueller Heimstand
 * @param awayScore aktueller Gaststand
 * @param kickoff   Anpfiff (UTC) — Tie-Breaker bei mehreren Live-Spielen
 * @param lastChange Zeitpunkt der letzten Stand-Änderung (FR-013); {@code null}, falls
 *                   seit Beobachtungsbeginn unverändert
 */
public record LiveMatchView(
        long matchId,
        String home,
        String away,
        int homeScore,
        int awayScore,
        Instant kickoff,
        Instant lastChange) {
}
