package com.example.wmtippspiel.publicapi.dto;

/**
 * Verteilung der Punktstufen eines Spielers (Feature 008): Anzahl der Tipps mit
 * 4, 3, 2 bzw. 0 Punkten (CHECK24-Schema).
 */
public record PointDistributionDto(int p4, int p3, int p2, int p0) {
}
