package com.example.wmtippspiel.live;

import com.example.wmtippspiel.domain.model.Match;

/**
 * Flüchtiges Tor-/Korrektur-Ereignis (F8). Wird von einer {@link GoalEventSource}
 * erzeugt und vom GoalNotifier ausgegeben. {@code newHome}/{@code newAway} sind
 * der laufende Stand nach genau diesem Ereignis.
 */
public record GoalEvent(
        long matchId,
        String home,
        String away,
        Kind kind,
        ScoringTeam scoringTeam,
        int newHome,
        int newAway,
        Integer minute) {

    public enum Kind { GOAL, CORRECTION }

    public enum ScoringTeam { HOME, AWAY }

    public static GoalEvent goal(Match match, ScoringTeam team, int newHome, int newAway, Integer minute) {
        return new GoalEvent(match.id(), match.home(), match.away(), Kind.GOAL, team, newHome, newAway, minute);
    }

    public static GoalEvent correction(Match match, int newHome, int newAway) {
        return new GoalEvent(match.id(), match.home(), match.away(), Kind.CORRECTION, null, newHome, newAway, null);
    }
}
