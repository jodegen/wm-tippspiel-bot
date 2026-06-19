package com.example.wmtippspiel.discord.render;

import java.util.Optional;

import com.example.wmtippspiel.config.AppProperties;
import com.example.wmtippspiel.publicapi.PublicIdService;

import org.springframework.stereotype.Component;

/**
 * Zentraler, zustandsloser Helfer für die Website-Hinweise in den Discord-Ausgaben
 * (Feature 009): kapselt Normalisierung der Basis-URL ({@code app.website.base-url})
 * und den Bau der konkreten Links/Hinweise.
 *
 * <p>Ist keine Basis-URL konfiguriert (leer/blank), liefern alle Methoden
 * {@link Optional#empty()} — die aufrufenden Embeds lassen den Hinweis dann fehlerfrei
 * weg (FR-006). In Profil-URLs erscheint ausschließlich der nicht-reversible
 * öffentliche {@code publicId} (identisch zu den Public-Endpoints, Feature 008),
 * <b>nie</b> die Discord-ID (FR-009).
 */
@Component
public class WebsiteLinks {

    /** Normalisierte Basis-URL ohne abschließenden Slash, oder {@code null} wenn nicht konfiguriert. */
    private final String baseUrl;
    private final PublicIdService publicIds;

    public WebsiteLinks(AppProperties properties, PublicIdService publicIds) {
        String configured = properties.website() == null ? null : properties.website().baseUrl();
        this.baseUrl = normalizeBase(configured);
        this.publicIds = publicIds;
    }

    /** {@code true}, wenn eine nicht-leere Basis-URL konfiguriert ist. */
    public boolean isConfigured() {
        return baseUrl != null;
    }

    /** Klickbare URL zur vollständigen Web-Tabelle ({@code {base}/leaderboard}). */
    public Optional<String> leaderboardUrl() {
        return isConfigured() ? Optional.of(baseUrl + "/leaderboard") : Optional.empty();
    }

    /**
     * Klickbare URL zur Web-Profilseite des Nutzers ({@code {base}/profil/{publicId}}).
     * Die Discord-ID wird nur zur Ableitung des {@code publicId} genutzt und erscheint
     * nicht in der URL.
     */
    public Optional<String> profileUrl(String discordUserId) {
        if (!isConfigured() || discordUserId == null || discordUserId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(baseUrl + "/profil/" + publicIds.publicId(discordUserId));
    }

    /** Host-Klartext für den (nicht klickbaren) Footer, z. B. {@code "wm.xenoria.de"}. */
    public Optional<String> host() {
        if (!isConfigured()) {
            return Optional.empty();
        }
        String h = baseUrl;
        int scheme = h.indexOf("://");
        if (scheme >= 0) {
            h = h.substring(scheme + 3);
        }
        if (h.startsWith("www.")) {
            h = h.substring(4);
        }
        return Optional.of(h);
    }

    /** Footer-Zusatz für das Leaderboard-Board, z. B. {@code "Vollständige Tabelle auf wm.xenoria.de"}. */
    public Optional<String> footerHint() {
        return host().map(h -> "Vollständige Tabelle auf " + h);
    }

    /** Entfernt führende/abschließende Leerzeichen und alle abschließenden Slashes (FR-008). */
    private static String normalizeBase(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.strip();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isBlank() ? null : trimmed;
    }
}
