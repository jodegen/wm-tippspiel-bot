package com.example.wmtippspiel.leaderboard;

/**
 * Rang-Veränderung eines Teilnehmers gegenüber dem vorigen Auswertungs-Batch
 * (F11). {@code previousRank == null} ⇒ erstmaliger Einstieg in die Wertung.
 */
public record RankDelta(int currentRank, Integer previousRank) {

    /** Anzeige-Symbol: {@code NEU} / {@code ↑n} / {@code ↓n} / {@code –}. */
    public String symbol() {
        if (previousRank == null) {
            return "NEU";
        }
        int improvement = previousRank - currentRank;
        if (improvement > 0) {
            return "↑" + improvement;
        }
        if (improvement < 0) {
            return "↓" + (-improvement);
        }
        return "–";
    }
}
