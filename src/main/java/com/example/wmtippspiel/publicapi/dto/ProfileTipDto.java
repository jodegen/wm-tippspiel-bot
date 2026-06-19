package com.example.wmtippspiel.publicapi.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Öffentliche Sicht eines gewerteten Tipps in der Profil-Historie (Feature 008):
 * Begegnung, getipptes und tatsächliches Ergebnis sowie Punkte. {@code matchId}/
 * {@code kickoffUtc}/{@code stage} stellen den Spielbezug her (Unterscheidung von
 * Wiederholungsbegegnungen, Verlinkung); {@code matchId} passt zu
 * {@code GET /matches/{matchId}/tips}. Keine sensiblen Felder.
 */
public record ProfileTipDto(
        String home,
        String away,
        int tipHome,
        int tipAway,
        Integer resultHome,
        Integer resultAway,
        int points,
        @Schema(description = "Öffentliche Fixture-ID des Spiels; passt zu GET /matches/{matchId}/tips") long matchId,
        @Schema(description = "Anstoßzeit in UTC (ISO-8601), z. B. 2026-06-20T19:00:00Z") Instant kickoffUtc,
        @Schema(description = "Turnierphase (gleiche Werte wie im Spielplan)", example = "GROUP_STAGE") String stage) {
}
