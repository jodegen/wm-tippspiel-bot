package com.example.wmtippspiel.leaderboard;

import com.example.wmtippspiel.persistence.LeaderboardEntry;

/** Eine gerankte Ranglisten-Zeile inkl. Rang-Veränderung (F11). */
public record RankedRow(LeaderboardEntry entry, int rank, RankDelta delta) {
}
