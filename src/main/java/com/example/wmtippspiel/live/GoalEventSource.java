package com.example.wmtippspiel.live;

import java.util.List;

/**
 * Austauschbare Quelle für Tor-/Korrektur-Ereignisse (FR-012). Default ist
 * Score-Diff-Polling ({@link ScoreDiffGoalEventSource}); später kann dieselbe
 * Schnittstelle von einer Push-Quelle (Webhook/WebSocket) implementiert werden,
 * ohne dass GoalDetector oder das Posting sich ändern.
 */
public interface GoalEventSource {

    List<GoalEvent> fetchEvents();
}
