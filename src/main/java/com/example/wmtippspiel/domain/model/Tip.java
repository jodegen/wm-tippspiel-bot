package com.example.wmtippspiel.domain.model;

import java.time.Instant;

/**
 * Vorhersage eines Mitglieds für ein Spiel. Eindeutig je
 * {@code (userId, matchId)}; bis Anpfiff aktualisierbar. {@code points} wird
 * erst durch die Auswertung gesetzt (Default 0).
 */
public record Tip(
        String userId,
        long matchId,
        String username,
        int homeScore,
        int awayScore,
        Instant createdAt,
        int points) {
}
