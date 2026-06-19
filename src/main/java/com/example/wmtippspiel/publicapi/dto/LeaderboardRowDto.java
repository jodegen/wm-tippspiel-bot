package com.example.wmtippspiel.publicapi.dto;

/**
 * Öffentliche Leaderboard-Zeile (Feature 008). {@code exactHits} stammt aus dem
 * direkten Tipp-Ergebnis-Vergleich (FR-010), {@code rankChange} ist das
 * Anzeige-Symbol der Rang-Veränderung (z. B. {@code NEU}, {@code ↑2}, {@code –}).
 * {@code publicId} ist der stabile, nicht zurückrechenbare HMAC-Identifier
 * (identisch zu {@code GET /players/{publicId}}) für die Verlinkung aufs Profil —
 * KEIN Discord-Identifikator.
 */
public record LeaderboardRowDto(
        int rank,
        String displayName,
        int points,
        int exactHits,
        String rankChange,
        String publicId) {
}
