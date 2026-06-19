package com.example.wmtippspiel.publicapi.dto;

import java.time.Instant;

/**
 * Öffentliche Sicht eines aktuell laufenden Spiels (Feature 008) mit aktuellem
 * Zwischenstand. Nur Spiele mit Status {@code IN_PLAY}.
 */
public record LiveMatchDto(
        long matchId,
        String home,
        String away,
        Instant kickoffUtc,
        Integer homeScore,
        Integer awayScore,
        String status,
        String group,
        Integer matchday) {
}
