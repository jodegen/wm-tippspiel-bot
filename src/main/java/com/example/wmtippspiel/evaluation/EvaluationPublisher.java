package com.example.wmtippspiel.evaluation;

import java.util.List;

import com.example.wmtippspiel.domain.model.Match;

/**
 * Veröffentlicht die Ergebnis-/Punkteübersicht eines ausgewerteten Spiels.
 * {@code correction = true} kennzeichnet eine Neubewertung nach Endstand-
 * Korrektur (FR-017a). Interface, damit die Auswertungslogik ohne Discord
 * test-first geprüft werden kann (Verfassung Prinzip III).
 */
public interface EvaluationPublisher {

    void publishEvaluation(Match match, List<ScoredTip> scoredTips, boolean correction);
}
