package com.example.wmtippspiel.domain.model;

/** Lebenszyklus-Status eines Spiels (vgl. data-model.md). */
public enum MatchStatus {
    SCHEDULED,
    IN_PLAY,
    FINISHED,
    POSTPONED,
    CANCELLED
}
