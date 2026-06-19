package com.example.wmtippspiel.publicapi.dto;

/**
 * Öffentliche Sicht eines gewerteten Tipps in der Profil-Historie (Feature 008):
 * Begegnung, getipptes und tatsächliches Ergebnis sowie Punkte.
 */
public record ProfileTipDto(
        String home,
        String away,
        int tipHome,
        int tipAway,
        Integer resultHome,
        Integer resultAway,
        int points) {
}
