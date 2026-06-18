package com.example.wmtippspiel.persistence;

import java.math.BigDecimal;

/**
 * Ein ausgewerteter Einzeltipp eines Spieltags (F12) inkl. tatsächlichem Ergebnis
 * und Quoten — Grundlage für die Auswahl des „besten Einzeltipps".
 * {@code resultHome}/{@code resultAway} und die Quoten können {@code null} sein.
 */
public record MatchdayTipRow(
        String username,
        int tipHome,
        int tipAway,
        int points,
        Integer resultHome,
        Integer resultAway,
        String home,
        String away,
        BigDecimal oddsHome,
        BigDecimal oddsDraw,
        BigDecimal oddsAway) {
}
