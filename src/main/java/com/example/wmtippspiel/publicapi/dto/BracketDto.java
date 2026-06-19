package com.example.wmtippspiel.publicapi.dto;

import java.util.List;

/**
 * Der komplette K.o.-Turnierbaum (Feature 010): genau sechs Runden in fester
 * Reihenfolge (LAST_32 → LAST_16 → QUARTER_FINALS → SEMI_FINALS → THIRD_PLACE →
 * FINAL).
 */
public record BracketDto(List<BracketRoundDto> rounds) {
}
