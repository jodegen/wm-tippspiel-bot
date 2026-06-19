package com.example.wmtippspiel.publicapi.dto;

import java.math.BigDecimal;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Öffentliche Spielplan-Zeile (Feature 008). Enthält ausschließlich
 * unbedenkliche Felder; {@code matchId} ist die öffentliche football-data-
 * Fixture-Referenz (FR-003). Zeit als UTC-{@link Instant} (FR-005); die
 * Anzeige-Formatierung übernimmt das Frontend.
 */
public record MatchDto(
        @Schema(description = "Öffentliche Fixture-ID (football-data)") long matchId,
        String home,
        String away,
        @Schema(description = "Anstoßzeit in UTC (ISO-8601), z. B. 2026-06-20T19:00:00Z") Instant kickoffUtc,
        @Schema(description = "Turnierphase", example = "GROUP_STAGE") String stage,
        String group,
        String tvChannel,
        BigDecimal oddsHome,
        BigDecimal oddsDraw,
        BigDecimal oddsAway,
        Integer homeScore,
        Integer awayScore,
        String status,
        Integer matchday) {
}
