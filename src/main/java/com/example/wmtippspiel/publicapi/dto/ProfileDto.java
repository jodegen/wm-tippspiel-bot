package com.example.wmtippspiel.publicapi.dto;

import java.util.List;

/**
 * Öffentliches Spielerprofil (Feature 008). Adressiert über den stabilen,
 * nicht-sensiblen {@code publicId} (HMAC, NICHT die Discord-user_id, FR-015).
 * {@code rank}/{@code hitRatePercent} sind {@code null}, {@code bestTip}/
 * {@code worstTip} fehlen, wenn keine gewerteten Tipps vorliegen (FR-018).
 * {@code history} enthält ausschließlich gewertete Tipps (Reveal-Regel, FR-017).
 */
public record ProfileDto(
        String publicId,
        String displayName,
        Integer rank,
        int points,
        int exactHits,
        int evaluatedTips,
        Integer hitRatePercent,
        PointDistributionDto distribution,
        ProfileTipDto bestTip,
        ProfileTipDto worstTip,
        List<ProfileTipDto> history) {
}
