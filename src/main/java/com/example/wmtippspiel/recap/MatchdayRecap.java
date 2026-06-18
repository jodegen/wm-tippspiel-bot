package com.example.wmtippspiel.recap;

import java.util.List;
import java.util.Optional;

import com.example.wmtippspiel.persistence.MatchdayScore;
import com.example.wmtippspiel.persistence.MatchdayTipRow;

/**
 * Aufbereiteter Inhalt eines Spieltags-Rückblicks (F12): Top-Punktesammler,
 * bester Einzeltipp und die leer ausgegangenen Teilnehmer.
 */
public record MatchdayRecap(
        String label,
        List<MatchdayScore> topScorers,
        Optional<MatchdayTipRow> bestTip,
        List<String> emptyHanded) {
}
