package com.example.wmtippspiel.persistence;

/**
 * Ein ausgewerteter Tipp eines Nutzers (F13) mit Begegnung, getipptem und
 * tatsächlichem Ergebnis sowie vergebenen Punkten — Grundlage für Verteilung
 * und besten/schlechtesten Tipp.
 */
public record ProfileTipRow(
        String home,
        String away,
        int tipHome,
        int tipAway,
        Integer resultHome,
        Integer resultAway,
        int points) {
}
