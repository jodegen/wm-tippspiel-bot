package com.example.wmtippspiel.persistence;

/**
 * Aggregierte Ranglisten-Zeile je Teilnehmer (FR-018/019). Sortierung erfolgt in
 * der Query: Gesamtpunkte ↓, dann exakte Treffer ↓ (Tie-Breaker, FR-020).
 */
public record LeaderboardEntry(
        String userId,
        String username,
        int totalPoints,
        int tipCount,
        int exactHits) {
}
