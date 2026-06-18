package com.example.wmtippspiel.leaderboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.wmtippspiel.persistence.LeaderboardEntry;

/**
 * Reine (Discord-/DB-freie) Ranglisten-Logik für das Leaderboard-Board (F11):
 * Standard-Competition-Ranking (gleicher Rang bei gleichen Punkten UND exakten
 * Treffern, identisch zu {@code /rangliste}) und die Rang-Veränderung gegen den
 * vorigen Auswertungs-Batch. Erwartet die Einträge bereits sortiert
 * (Punkte ↓, exakte Treffer ↓).
 */
public final class LeaderboardRanking {

    private LeaderboardRanking() {
    }

    /**
     * Weist Ränge zu und berechnet je Eintrag die Veränderung gegen
     * {@code previousRanks} (Map userId → Rang aus dem vorigen Batch).
     */
    public static List<RankedRow> compute(List<LeaderboardEntry> sorted, Map<String, Integer> previousRanks) {
        List<RankedRow> rows = new ArrayList<>(sorted.size());
        int rank = 0;
        int index = 0;
        LeaderboardEntry previous = null;
        for (LeaderboardEntry e : sorted) {
            index++;
            if (previous == null
                    || e.totalPoints() != previous.totalPoints()
                    || e.exactHits() != previous.exactHits()) {
                rank = index;
            }
            rows.add(new RankedRow(e, rank, new RankDelta(rank, previousRanks.get(e.userId()))));
            previous = e;
        }
        return rows;
    }

    /** Ränge aller Teilnehmer als Map userId → Rang (neue Vergleichsbasis/Snapshot). */
    public static Map<String, Integer> ranksByUser(List<RankedRow> rows) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (RankedRow r : rows) {
            map.put(r.entry().userId(), r.rank());
        }
        return map;
    }
}
