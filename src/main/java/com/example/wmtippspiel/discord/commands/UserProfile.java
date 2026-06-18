package com.example.wmtippspiel.discord.commands;

import com.example.wmtippspiel.persistence.ProfileTipRow;

/**
 * Aufbereitete persönliche Bilanz (F13). {@code rank}/{@code hitRatePercent} sind
 * {@code null} bzw. {@code best}/{@code worst} leer, wenn (noch) keine
 * ausgewerteten Tipps vorliegen.
 */
public record UserProfile(
        String username,
        Integer rank,
        int totalPoints,
        int exactHits,
        int evaluatedTips,
        Integer hitRatePercent,
        int count4,
        int count3,
        int count2,
        int count0,
        ProfileTipRow best,
        ProfileTipRow worst) {

    /** Keine ausgewerteten Tipps und kein Wertungsstand vorhanden. */
    public boolean isEmpty() {
        return evaluatedTips == 0 && totalPoints == 0 && rank == null;
    }
}
