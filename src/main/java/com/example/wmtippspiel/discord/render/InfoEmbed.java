package com.example.wmtippspiel.discord.render;

import java.awt.Color;
import java.time.Clock;

import com.example.wmtippspiel.config.AppProperties;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import org.springframework.stereotype.Component;

/**
 * Baut das dauerhafte Info-/Anleitungs-Embed für den Info-Channel – genau eine
 * Nachricht, die der Bot pflegt. Verweise auf Announce-/Board-Channel werden,
 * falls konfiguriert, als klickbare Channel-Mentions gerendert.
 */
@Component
public class InfoEmbed {

    private static final String DIVIDER = "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬";

    private final AppProperties properties;
    private final Clock clock;

    public InfoEmbed(AppProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public MessageEmbed build() {
        AppProperties.Discord d = properties.discord();
        String announce = mention(d.announceChannelId(), "im Announce-Channel");
        String board = mention(d.boardChannelId(), "das Live-Board");
        String tip = mention(d.tipChannelId(), null);

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(new Color(0xF1C40F))
                .setAuthor("FIFA WM 2026 · 11. Juni – 19. Juli", null, null)
                .setTitle("⚽  WM 2026 Tippspiel — So funktioniert's")
                .setDescription("""
                        Tippe die Spiele der Weltmeisterschaft und sammle Punkte. \
                        Alles Wichtige auf einen Blick — viel Erfolg! 🍀
                        """ + "\n" + DIVIDER);

        String tippHint = tip != null
                ? "Am einfachsten in " + tip + ": auf **\"⚽ Jetzt tippen\"** klicken, Spiel wählen, Ergebnis eingeben.\n"
                : "Nutze **`/tippen`** — Spiel auswählen und Ergebnis ins Popup eintragen (oder **`/tipp`** direkt).\n";
        embed.addField("🎯  Tipp abgeben",
                tippHint + "Deine Abgabe ist **nur für dich sichtbar** und bis zum **Anpfiff** beliebig änderbar.",
                false);

        embed.addField("📊  Punkte",
                "🎯  **3** — exaktes Ergebnis\n"
                        + "✅  **1** — richtige Tendenz\n"
                        + "❌  **0** — daneben",
                true);

        embed.addField("🧭  Weitere Befehle",
                "**`/rangliste`** — Tabelle\n"
                        + "**`/spielplan`** — nächste Spiele\n"
                        + "**`/naechstes`** — nächstes Spiel",
                true);

        embed.addField("🔓  Ablauf",
                "Bei **Anpfiff** werden alle Tipps offengelegt, nach **Abpfiff** automatisch "
                        + "ausgewertet — " + announce + ".\n"
                        + "Den aktuellen Spielplan findest du in " + board + ".",
                false);

        embed.setFooter("Alle Zeiten in Europe/Berlin · Fair play! 🤝");
        embed.setTimestamp(clock.instant());
        return embed.build();
    }

    /** Channel-Mention (<#id>), falls konfiguriert; sonst der Fallback-Text. */
    private String mention(String channelId, String fallback) {
        return channelId != null && !channelId.isBlank() ? "<#" + channelId + ">" : fallback;
    }
}
