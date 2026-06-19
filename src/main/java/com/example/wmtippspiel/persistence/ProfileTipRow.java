package com.example.wmtippspiel.persistence;

import java.time.Instant;

/**
 * Ein ausgewerteter Tipp eines Nutzers (F13) mit Begegnung, getipptem und
 * tatsächlichem Ergebnis sowie vergebenen Punkten — Grundlage für Verteilung
 * und besten/schlechtesten Tipp. {@code matchId}/{@code kickoffUtc}/{@code stage}
 * stammen aus dem zugehörigen Spiel und erlauben das Unterscheiden von
 * Wiederholungsbegegnungen sowie die Verlinkung aufs Spiel.
 */
public record ProfileTipRow(
        String home,
        String away,
        int tipHome,
        int tipAway,
        Integer resultHome,
        Integer resultAway,
        int points,
        long matchId,
        Instant kickoffUtc,
        String stage) {
}
