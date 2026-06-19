package com.example.wmtippspiel.publicapi.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Öffentliches Spielerprofil (Feature 008). Adressiert über den stabilen,
 * nicht-sensiblen {@code publicId} (HMAC, NICHT die Discord-user_id, FR-015).
 * {@code rank}/{@code hitRatePercent} sind {@code null}, {@code bestTip}/
 * {@code worstTip} fehlen, wenn keine gewerteten Tipps vorliegen (FR-018).
 * {@code history} enthält ausschließlich gewertete Tipps (Reveal-Regel, FR-017).
 */
public record ProfileDto(
        @Schema(description = "Stabiler, nicht zurückrechenbarer öffentlicher Identifier (NICHT die Discord-ID)")
        String publicId,
        String displayName,
        @Schema(description = "Platzierung; null, wenn (noch) nicht in der Wertung") Integer rank,
        int points,
        int exactHits,
        int evaluatedTips,
        @Schema(description = "Trefferquote in Prozent; null bei 0 gewerteten Tipps") Integer hitRatePercent,
        PointDistributionDto distribution,
        ProfileTipDto bestTip,
        ProfileTipDto worstTip,
        @Schema(description = "Nur bereits gewertete Tipps (Reveal-Regel)") List<ProfileTipDto> history) {
}
