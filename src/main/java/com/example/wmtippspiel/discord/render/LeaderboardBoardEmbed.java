package com.example.wmtippspiel.discord.render;

import java.util.List;

import com.example.wmtippspiel.leaderboard.RankedRow;
import com.example.wmtippspiel.persistence.LeaderboardEntry;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import org.springframework.stereotype.Component;

/**
 * Baut das selbst-aktualisierende Leaderboard-Board (F11) im gemeinsamen Stil
 * ({@link EmbedStyle}, FR-011): kompakte Top-N-Liste in der Beschreibung mit Rang,
 * Name, Punkten, exakten Treffern und Rang-Veränderung; defensiv tronkiert unter
 * den Discord-Embed-Limits (FR-008).
 *
 * <p>Feature 009: Ist eine Website-Basis-URL konfiguriert, wird der Footer gezielt um
 * einen dezenten Hinweis auf die vollständige Web-Tabelle ergänzt (FR-001). Der Hinweis
 * ist eine kurze, feste Konstante (kein dynamischer Langtext) und bleibt damit sicher
 * unter den Discord-Footer-Limits (FR-010). {@link EmbedStyle#FOOTER_BASE} bleibt global
 * unverändert — die Ergänzung gilt nur für dieses Board (kein Seiteneffekt auf andere
 * Embeds).
 */
@Component
public class LeaderboardBoardEmbed {

    /** Sicherheits-Limit für die Beschreibung (Discord-Hardlimit ist 4096). */
    static final int SAFE_DESC_LIMIT = 4000;

    private static final String TITLE = "🏆  Rangliste";

    private final EmbedStyle style;
    private final WebsiteLinks websiteLinks;

    public LeaderboardBoardEmbed(EmbedStyle style, WebsiteLinks websiteLinks) {
        this.style = style;
        this.websiteLinks = websiteLinks;
    }

    /** Baut das Board mit höchstens {@code topN} Zeilen. */
    public MessageEmbed build(List<RankedRow> rows, int topN) {
        EmbedBuilder embed = style.base(TITLE);
        // Footer-Hinweis auf die Web-Tabelle (Feature 009) – auch im Leer-Board-Fall,
        // da er nicht von Tipp-Daten abhängt. Footer ist reiner Text (nicht klickbar).
        websiteLinks.footerHint().ifPresent(hint ->
                embed.setFooter(EmbedStyle.FOOTER_BASE + " · " + hint));
        if (rows.isEmpty()) {
            embed.setDescription("Noch keine Tipps gewertet. 🏁");
            return embed.build();
        }
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (RankedRow r : rows) {
            if (shown >= topN) {
                break;
            }
            String line = line(r);
            if (sb.length() + line.length() > SAFE_DESC_LIMIT) {
                break;
            }
            sb.append(line);
            shown++;
        }
        embed.setDescription(sb.toString());
        return embed.build();
    }

    private String line(RankedRow r) {
        LeaderboardEntry e = r.entry();
        return "**" + r.rank() + ".** " + e.username()
                + " — **" + e.totalPoints() + "** Pkt · "
                + e.exactHits() + "× exakt  " + r.delta().symbol() + "\n";
    }
}
