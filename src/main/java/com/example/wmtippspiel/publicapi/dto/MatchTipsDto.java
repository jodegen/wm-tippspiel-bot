package com.example.wmtippspiel.publicapi.dto;

import java.util.List;

/**
 * Öffentliche Antwort des Tipps-pro-Spiel-Endpoints (Feature 008).
 * {@code released} ist nur {@code true}, wenn das Spiel angepfiffen ist
 * ({@code now() (UTC) ≥ kickoff} UND {@code revealed}); andernfalls ist
 * {@code tips} leer und enthält keinerlei fremde Einzeltipps (FR-012/013).
 */
public record MatchTipsDto(
        long matchId,
        boolean released,
        List<PublicTipDto> tips) {

    /** Reveal-gesperrte Antwort: keine Tipps preisgegeben. */
    public static MatchTipsDto locked(long matchId) {
        return new MatchTipsDto(matchId, false, List.of());
    }
}
