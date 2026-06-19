package com.example.wmtippspiel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Externalisierte Konfiguration (Geheimnisse über Umgebung, nicht im Repo). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String timezoneDisplay,
        Discord discord,
        Leaderboard leaderboard,
        FootballData footballData,
        Odds odds,
        PublicApi publicApi,
        Website website) {

    public record Discord(String token, String guildId, String announceChannelId, String boardChannelId,
                          String infoChannelId, String tipChannelId, String notifyRoleId,
                          String leaderboardChannelId) {
    }

    /**
     * Konfiguration der öffentlichen Website (Feature 009).
     *
     * @param baseUrl Basis-URL der öffentlichen Website (z. B. {@code https://wm.xenoria.de}).
     *                Leer ⇒ Website-Hinweise/Links in den Discord-Ausgaben werden ausgelassen.
     *                Bewusst getrennt von {@code app.public-api.public-base-url} (API-Host).
     */
    public record Website(String baseUrl) {
    }

    /**
     * Konfiguration der öffentlichen Read-only-API (Feature 008).
     *
     * @param corsAllowedOrigins erlaubte CORS-Origins (z. B. Vercel-Domain)
     * @param idSecret           HMAC-Secret für den öffentlichen Spieler-Identifier (PFLICHT)
     * @param cacheTtlSeconds    TTL des leichten Caches für Spielplan/Leaderboard
     */
    public record PublicApi(java.util.List<String> corsAllowedOrigins, String idSecret, long cacheTtlSeconds) {
    }

    /** Konfiguration des Leaderboard-Boards (F11). */
    public record Leaderboard(int topN) {
    }

    public record FootballData(String baseUrl, String apiKey) {
    }

    public record Odds(boolean enabled, String baseUrl, String apiKey) {
    }
}
