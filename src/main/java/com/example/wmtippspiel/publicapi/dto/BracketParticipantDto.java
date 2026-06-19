package com.example.wmtippspiel.publicapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Ein Beteiligter eines Bracket-Spiels (Feature 010). Genau eines von
 * {@code teamName}/{@code placeholder} ist gesetzt: {@code teamName}, sobald das
 * Team feststeht; sonst {@code placeholder} mit beschreibendem Label (FR-008,
 * SC-005). Enthält keine sensiblen Felder.
 */
public record BracketParticipantDto(
        @Schema(description = "Feststehendes Team", example = "Deutschland") String teamName,
        @Schema(description = "Platzhalter, solange offen", example = "Sieger Gruppe A") String placeholder) {

    public static BracketParticipantDto team(String name) {
        return new BracketParticipantDto(name, null);
    }

    public static BracketParticipantDto placeholderOf(String label) {
        return new BracketParticipantDto(null, label);
    }
}
