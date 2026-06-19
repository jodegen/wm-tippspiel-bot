package com.example.wmtippspiel.publicapi.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Eine K.o.-Runde des Baums (Feature 010) mit ihren Spielen in Slot-Reihenfolge.
 */
public record BracketRoundDto(
        @Schema(description = "Turnierphase (football-data-Vokabular)", example = "LAST_32") String stage,
        @Schema(description = "Anzeigename der Runde", example = "Sechzehntelfinale") String label,
        List<BracketMatchDto> matches) {
}
