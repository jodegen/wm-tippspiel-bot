package com.example.wmtippspiel.publicapi.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Öffentliche Spielplan-Zeile (Feature 008). Enthält ausschließlich
 * unbedenkliche Felder; {@code matchId} ist die öffentliche football-data-
 * Fixture-Referenz (FR-003). Zeit als UTC-{@link Instant} (FR-005); die
 * Anzeige-Formatierung übernimmt das Frontend.
 */
public record MatchDto(
        long matchId,
        String home,
        String away,
        Instant kickoffUtc,
        String stage,
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
