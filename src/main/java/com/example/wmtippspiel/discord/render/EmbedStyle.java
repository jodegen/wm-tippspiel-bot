package com.example.wmtippspiel.discord.render;

import java.awt.Color;
import java.time.Clock;

import net.dv8tion.jda.api.EmbedBuilder;

import org.springframework.stereotype.Component;

/**
 * Gemeinsamer Styling-Helper für die dauerhaft gepflegten Embeds (Info-Channel
 * und Live-Board). Zentralisiert Akzentfarbe, Author-Header, Footer und den
 * Zeitstempel, damit Info- und Board-Embed sichtbar zur selben „Familie" gehören
 * (F7-Redesign, FR-010/011). Der Zeitstempel kommt aus der injizierten
 * {@link Clock} (Verfassung Prinzip IV – kein {@code Instant.now()}).
 */
@Component
public class EmbedStyle {

    /** Marken-Akzentfarbe (gemeinsam für Info & Board). */
    public static final Color ACCENT = new Color(0xF1C40F);

    /** Optischer Trenner für Beschreibungen. */
    public static final String DIVIDER = "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬";

    /** Author-Zeile (Header) – identisch über alle gepflegten Embeds. */
    public static final String AUTHOR = "FIFA WM 2026 · 11. Juni – 19. Juli";

    /** Basis-Footer-Text; Aufrufer dürfen ihn überschreiben (Timestamp bleibt). */
    public static final String FOOTER_BASE = "Alle Zeiten in Europe/Berlin";

    private final Clock clock;

    public EmbedStyle(Clock clock) {
        this.clock = clock;
    }

    /**
     * Vorgestylter Builder mit Akzentfarbe, Author-Header, Titel, Basis-Footer und
     * aktuellem Zeitstempel. Für die dauerhaften Channel-Embeds (Info, Board).
     */
    public EmbedBuilder base(String title) {
        return new EmbedBuilder()
                .setColor(ACCENT)
                .setAuthor(AUTHOR, null, null)
                .setTitle(title)
                .setFooter(FOOTER_BASE)
                .setTimestamp(clock.instant());
    }

    /**
     * Wie {@link #base(String)}, aber ohne Author-Zeile – für kompaktere bzw.
     * ephemerale Ansichten, die denselben Akzent/Footer beibehalten.
     */
    public EmbedBuilder bare(String title) {
        return new EmbedBuilder()
                .setColor(ACCENT)
                .setTitle(title)
                .setFooter(FOOTER_BASE)
                .setTimestamp(clock.instant());
    }
}
