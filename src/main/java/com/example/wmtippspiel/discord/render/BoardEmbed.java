package com.example.wmtippspiel.discord.render;

import java.awt.Color;
import java.util.List;

import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.MatchStatus;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import org.springframework.stereotype.Component;

/**
 * Baut die Board-Embeds (F7-Redesign).
 * <ul>
 *   <li>{@link #buildBoard(List)} – das <b>eine</b> konsolidierte Board-Embed:
 *       die nächsten anstehenden Spiele als zusammenhängende Liste in der
 *       Beschreibung, im Info-Look ({@link EmbedStyle}), defensiv tronkiert unter
 *       Beachtung der Discord-Limits (FR-002/003/004/005/009/010).</li>
 *   <li>{@link #buildFiltered(String, List)} – unveränderte ephemerale
 *       Filteransicht (darf Live-/Endstand zeigen).</li>
 * </ul>
 */
@Component
public class BoardEmbed {

    /** Sicherheits-Limit für die Beschreibung (Discord-Hardlimit ist 4096). */
    static final int SAFE_DESC_LIMIT = 4000;

    private static final String BOARD_TITLE = "📅  Nächste Spiele";

    private final TimeFormatting time;
    private final EmbedStyle style;

    public BoardEmbed(TimeFormatting time, EmbedStyle style) {
        this.time = time;
        this.style = style;
    }

    /**
     * Konsolidiertes Board: EIN Embed, anstehende Spiele als Liste in der
     * Beschreibung. Bei Überlauf wird defensiv abgeschnitten und ein Hinweis
     * angehängt; Leerzustand erhält einen freundlichen Hinweis.
     */
    public MessageEmbed buildBoard(List<Match> upcoming) {
        EmbedBuilder embed = style.base(BOARD_TITLE);
        if (upcoming.isEmpty()) {
            embed.setDescription("Aktuell keine anstehenden Spiele. 🏁");
            return embed.build();
        }
        embed.setDescription(boardBody(upcoming));
        return embed.build();
    }

    /** Ephemerale Filteransicht (unverändert): zeigt ggf. Live-/Endstand. */
    public MessageEmbed buildFiltered(String title, List<Match> matches) {
        EmbedBuilder embed = new EmbedBuilder().setColor(new Color(0x2ECC71)).setTitle(title);
        embed.setDescription(matches.isEmpty() ? "Keine Spiele." : body(matches));
        return embed.build();
    }

    /** Listen-Beschreibung für das Board mit defensiver Truncation. */
    private String boardBody(List<Match> matches) {
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (Match m : matches) {
            String block = boardLine(m);
            // Reicht der Platz für den nächsten Block (plus möglichen Hinweis)?
            if (sb.length() + block.length() > SAFE_DESC_LIMIT) {
                int remaining = matches.size() - shown;
                sb.append("… und ").append(remaining).append(" weitere");
                break;
            }
            sb.append(block);
            shown++;
        }
        return sb.toString();
    }

    /** Ein Spielblock für das Board (nur Countdown, keine Live-/Endstände). */
    private String boardLine(Match m) {
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(m.home()).append(" vs ").append(m.away()).append("**\n")
                .append("⏱️ Anpfiff ").append(time.relative(m.kickoff()));
        if (m.channel() != null && !m.channel().isBlank()) {
            sb.append("  📺 ").append(m.channel());
        }
        appendOdds(sb, m);
        sb.append("\n\n");
        return sb.toString();
    }

    /** Body der Filteransicht (unverändert): Begegnung + Live-/Endstand oder Countdown. */
    private String body(List<Match> matches) {
        StringBuilder sb = new StringBuilder();
        for (Match m : matches) {
            sb.append("**").append(m.home()).append(" vs ").append(m.away()).append("**\n")
                    .append(scoreOrCountdown(m));
            if (m.channel() != null && !m.channel().isBlank()) {
                sb.append("  📺 ").append(m.channel());
            }
            appendOdds(sb, m);
            sb.append("\n\n");
        }
        return sb.toString();
    }

    private void appendOdds(StringBuilder sb, Match m) {
        if (m.oddsHome() != null && m.oddsDraw() != null && m.oddsAway() != null) {
            sb.append("  💰 ")
                    .append(m.oddsHome().stripTrailingZeros().toPlainString()).append('/')
                    .append(m.oddsDraw().stripTrailingZeros().toPlainString()).append('/')
                    .append(m.oddsAway().stripTrailingZeros().toPlainString());
        }
    }

    private String scoreOrCountdown(Match m) {
        if (m.homeScore() != null && m.awayScore() != null
                && (m.status() == MatchStatus.IN_PLAY || m.status() == MatchStatus.FINISHED)) {
            String tag = m.status() == MatchStatus.FINISHED ? "Endstand" : "Live";
            return tag + " **" + m.homeScore() + ":" + m.awayScore() + "**";
        }
        return "Anpfiff " + time.relative(m.kickoff());
    }
}
