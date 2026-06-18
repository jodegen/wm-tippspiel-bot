package com.example.wmtippspiel.discord.commands;

import java.util.Comparator;
import java.util.List;

import com.example.wmtippspiel.leaderboard.RankedRow;
import com.example.wmtippspiel.persistence.ProfileTipRow;

/**
 * Reine (Discord-/DB-freie) Aggregation der persönlichen Bilanz (F13). Liest
 * ausschließlich bereits ermittelte Werte (Rang/Punkte aus der Rangliste,
 * gespeicherte {@code points}) — berechnet keine Punkte neu (FR-026).
 */
public final class ProfileStats {

    private ProfileStats() {
    }

    /**
     * @param username angezeigter Name
     * @param mine     gerankte Ranglisten-Zeile des Nutzers (oder {@code null}, wenn er nicht in der Wertung ist)
     * @param tips     bereits ausgewertete Tipps des Nutzers
     */
    public static UserProfile build(String username, RankedRow mine, List<ProfileTipRow> tips) {
        int evaluated = tips.size();
        int c4 = (int) tips.stream().filter(t -> t.points() == 4).count();
        int c3 = (int) tips.stream().filter(t -> t.points() == 3).count();
        int c2 = (int) tips.stream().filter(t -> t.points() == 2).count();
        int c0 = (int) tips.stream().filter(t -> t.points() == 0).count();

        Integer rank = mine == null ? null : mine.rank();
        int totalPoints = mine == null ? 0 : mine.entry().totalPoints();
        int exactHits = mine == null ? 0 : mine.entry().exactHits();
        Integer hitRate = evaluated == 0 ? null : Math.round(100f * exactHits / evaluated);

        ProfileTipRow best = tips.stream().max(Comparator.comparingInt(ProfileTipRow::points)).orElse(null);
        ProfileTipRow worst = tips.stream().min(Comparator.comparingInt(ProfileTipRow::points)).orElse(null);

        return new UserProfile(username, rank, totalPoints, exactHits, evaluated, hitRate,
                c4, c3, c2, c0, best, worst);
    }
}
