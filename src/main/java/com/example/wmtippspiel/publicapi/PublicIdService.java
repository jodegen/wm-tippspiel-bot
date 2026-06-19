package com.example.wmtippspiel.publicapi;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.example.wmtippspiel.config.AppProperties;

import org.springframework.stereotype.Service;

/**
 * Leitet den stabilen, nicht-sensiblen öffentlichen Spieler-Identifier ab
 * (Feature 008, FR-015, Clarify Q1): {@code publicId = Base64Url(HMAC-SHA256(
 * secret, user_id))}, gekürzt. Deterministisch, stabil über Anzeigenamen-
 * Umbenennungen, nicht zurückrechenbar ohne Secret und ohne Persistenz/Schema.
 *
 * <p>Fail-Fast: Ist das Secret leer/blank, schlägt die Bean-Initialisierung fehl
 * (kein unsicherer Laufzeit-Default). Das Secret stammt aus der Umgebung
 * (Verfassung — Geheimnisse nicht im Repo).
 */
@Service
public class PublicIdService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int ID_LENGTH = 22;

    private final byte[] secret;

    public PublicIdService(AppProperties properties) {
        String configured = properties.publicApi() == null ? null : properties.publicApi().idSecret();
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException(
                    "app.public-api.id-secret (PUBLIC_API_ID_SECRET) muss gesetzt sein — kein unsicherer Default.");
        }
        this.secret = configured.getBytes(StandardCharsets.UTF_8);
    }

    /** Stabiler öffentlicher Identifier zur internen {@code userId} (nicht reversibel). */
    public String publicId(String userId) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(userId.getBytes(StandardCharsets.UTF_8));
            String full = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
            return full.substring(0, Math.min(ID_LENGTH, full.length()));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-Berechnung fehlgeschlagen", e);
        }
    }

    /** Löst einen {@code publicId} gegen die bekannten internen IDs auf (Enumeration). */
    public Optional<String> resolve(String publicId, Collection<String> candidateUserIds) {
        if (publicId == null) {
            return Optional.empty();
        }
        return candidateUserIds.stream().filter(id -> publicId.equals(publicId(id))).findFirst();
    }
}
