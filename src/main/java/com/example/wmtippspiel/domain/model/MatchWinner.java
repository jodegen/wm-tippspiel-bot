package com.example.wmtippspiel.domain.model;

/**
 * Endgültiger Sieger eines Spiels gemäß football-data {@code score.winner}
 * (Feature 010). Deckt insbesondere durch Verlängerung/Elfmeterschießen
 * entschiedene K.o.-Spiele ab, deren {@code home_score}/{@code away_score}
 * allein keinen Sieger erkennen lassen. {@code null} = (noch) unbekannt.
 */
public enum MatchWinner {
    HOME_TEAM,
    AWAY_TEAM,
    DRAW
}
