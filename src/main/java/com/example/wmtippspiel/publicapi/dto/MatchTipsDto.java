package com.example.wmtippspiel.publicapi.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Öffentliche Antwort des Tipps-pro-Spiel-Endpoints (Feature 008).
 * {@code released} ist nur {@code true}, wenn das Spiel angepfiffen ist
 * ({@code now() (UTC) ≥ kickoff} UND {@code revealed}); andernfalls ist
 * {@code tips} leer und enthält keinerlei fremde Einzeltipps (FR-012/013).
 */
public record MatchTipsDto(
        long matchId,
        @Schema(description = "true nur, wenn das Spiel angepfiffen ist (now() ≥ kickoff UND revealed). "
                + "Solange false, ist tips leer.") boolean released,
        @Schema(description = "Abgegebene Tipps; leer, solange released=false") List<PublicTipDto> tips) {

    /** Reveal-gesperrte Antwort: keine Tipps preisgegeben. */
    public static MatchTipsDto locked(long matchId) {
        return new MatchTipsDto(matchId, false, List.of());
    }
}
