package com.example.wmtippspiel.config;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Zeit-Beans gemäß Verfassung Prinzip IV: Logik rechnet in UTC, Anzeige in
 * Europe/Berlin. Die {@link Clock} wird in die Kernlogik injiziert, damit
 * Reveal-/Eval-Timing deterministisch testbar ist (Prinzip III).
 */
@Configuration
public class TimeConfig {

    /** UTC-Uhr als einzige Zeitquelle der Geschäftslogik. */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneOffset.UTC);
    }

    /** Anzeigezeitzone (Standard Europe/Berlin), nur an der Ausgabegrenze genutzt. */
    @Bean
    public ZoneId displayZone(AppProperties properties) {
        String zone = properties.timezoneDisplay();
        return ZoneId.of(zone == null || zone.isBlank() ? "Europe/Berlin" : zone);
    }
}
