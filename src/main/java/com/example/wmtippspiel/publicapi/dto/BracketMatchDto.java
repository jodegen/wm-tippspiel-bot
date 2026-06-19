package com.example.wmtippspiel.publicapi.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Ein Spiel im K.o.-Baum (Feature 010). {@code fifaMatchNo} (73–104) ist die
 * stabile Referenz; {@code sourceMatchNos}/{@code nextMatchNo} bilden die Kanten
 * ab. {@code matchId}/Ergebnis/{@code status}/{@code winner} sind nur gesetzt,
 * wenn ein reales Spiel zugeordnet bzw. entschieden ist. Nur unbedenkliche Felder.
 */
public record BracketMatchDto(
        @Schema(description = "Stabile FIFA-Match-Nummer 73–104", example = "89") int fifaMatchNo,
        @Schema(description = "Reale football-data-Fixture-ID, sofern vorhanden") Long matchId,
        BracketParticipantDto home,
        BracketParticipantDto away,
        Integer homeScore,
        Integer awayScore,
        @Schema(description = "Spielstatus", example = "FINISHED") String status,
        @Schema(description = "Sieger, sobald entschieden (inkl. Verlängerung/Elfmeter)", example = "HOME_TEAM") String winner,
        @Schema(description = "Quell-Matches (0 oder 2 FIFA-Nrn)", example = "[74, 77]") List<Integer> sourceMatchNos,
        @Schema(description = "Ziel-Match des Siegers; null für 103/104", example = "97") Integer nextMatchNo) {
}
