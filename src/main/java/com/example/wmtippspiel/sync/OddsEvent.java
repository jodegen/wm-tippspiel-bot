package com.example.wmtippspiel.sync;

import java.math.BigDecimal;

/** Quoten eines Events aus der Odds-API (Markt h2h). Werte können null sein. */
public record OddsEvent(
        String homeTeam,
        String awayTeam,
        BigDecimal oddsHome,
        BigDecimal oddsDraw,
        BigDecimal oddsAway) {
}
