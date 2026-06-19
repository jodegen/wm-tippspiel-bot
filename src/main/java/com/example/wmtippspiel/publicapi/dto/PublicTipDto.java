package com.example.wmtippspiel.publicapi.dto;

/**
 * Öffentliche Sicht eines abgegebenen Tipps (Feature 008). Nur Anzeigename und
 * getipptes Ergebnis; {@code points} nur bei bereits gewertetem Spiel, sonst
 * {@code null}. Wird ausschließlich nach Anpfiff ausgeliefert (FR-012/013).
 */
public record PublicTipDto(
        String displayName,
        int tipHome,
        int tipAway,
        Integer points) {
}
