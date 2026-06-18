package com.example.wmtippspiel.persistence;

/** Punktesumme eines Teilnehmers an einem Spieltag/Recap-Key (F12). */
public record MatchdayScore(String userId, String username, int points) {
}
