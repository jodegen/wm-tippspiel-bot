package com.example.wmtippspiel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Externalisierte Konfiguration (Geheimnisse über Umgebung, nicht im Repo). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String timezoneDisplay,
        Discord discord,
        FootballData footballData,
        Odds odds) {

    public record Discord(String token, String guildId, String announceChannelId, String boardChannelId,
                          String infoChannelId, String tipChannelId) {
    }

    public record FootballData(String baseUrl, String apiKey) {
    }

    public record Odds(boolean enabled, String baseUrl, String apiKey) {
    }
}
