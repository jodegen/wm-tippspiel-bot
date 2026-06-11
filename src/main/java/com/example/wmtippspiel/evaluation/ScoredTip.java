package com.example.wmtippspiel.evaluation;

/** Ergebnis der Auswertung eines einzelnen Tipps (für die Punkteübersicht). */
public record ScoredTip(String username, int homeTip, int awayTip, int points) {
}
