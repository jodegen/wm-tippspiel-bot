package com.example.wmtippspiel.discord.render;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import org.springframework.stereotype.Component;

/**
 * Call-to-Action-Embed für den Tipp-Channel (begleitet den „Jetzt tippen"-Button).
 * Teilt die gemeinsame Chrome (Akzent, Author-Header, Footer, Zeitstempel) aus
 * {@link EmbedStyle}, damit es sichtbar zur Info-/Board-Familie gehört, und weist
 * das CHECK24-Punkteschema 4/3/2/0 aus (F5).
 */
@Component
public class TipCtaEmbed {

    private final EmbedStyle style;

    public TipCtaEmbed(EmbedStyle style) {
        this.style = style;
    }

    public MessageEmbed build() {
        EmbedBuilder embed = style.base("⚽  Jetzt tippen!")
                .setDescription("""
                        Tippe die Spiele der WM 2026 und sammle Punkte — \
                        je näher am echten Ergebnis, desto mehr! 🍀
                        """ + "\n" + EmbedStyle.DIVIDER);

        embed.addField("🎮  So geht's",
                "Klick auf **„⚽ Jetzt tippen“**, wähle ein Spiel und gib dein Ergebnis ein.",
                false);

        embed.addField("📊  Punkte",
                "🎯  **4** — exaktes Ergebnis\n"
                        + "🔥  **3** — richtige Tordifferenz\n"
                        + "✅  **2** — richtige Tendenz\n"
                        + "❌  **0** — daneben",
                true);

        embed.addField("🔒  Privat & flexibel",
                "Nur **du** siehst deine Eingabe —\nbis zum **Anpfiff** beliebig änderbar.",
                true);

        return embed.build();
    }
}
