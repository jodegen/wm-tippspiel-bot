package com.example.wmtippspiel.discord.render;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * Wandelt UTC-{@link Instant} an der Anzeigegrenze in Europe/Berlin um
 * (Verfassung Prinzip IV). Für Discord werden zusätzlich client-seitige
 * Relative-/Full-Timestamps erzeugt ({@code <t:UNIX:R>} / {@code <t:UNIX:F>}).
 */
@Component
public class TimeFormatting {

    private static final DateTimeFormatter HUMAN =
            DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy HH:mm 'Uhr'", Locale.GERMANY);

    private final ZoneId displayZone;

    public TimeFormatting(ZoneId displayZone) {
        this.displayZone = displayZone;
    }

    /** Menschlich lesbare Zeit in der Anzeigezeitzone (z. B. Europe/Berlin). */
    public String human(Instant instant) {
        return HUMAN.format(instant.atZone(displayZone));
    }

    /** Discord-Relative-Timestamp (läuft client-seitig als Countdown). */
    public String relative(Instant instant) {
        return "<t:" + instant.getEpochSecond() + ":R>";
    }

    /** Discord-Full-Timestamp (client-lokale, vollständige Datums-/Zeitangabe). */
    public String full(Instant instant) {
        return "<t:" + instant.getEpochSecond() + ":F>";
    }
}
