package com.example.wmtippspiel.discord.render;

import java.awt.Color;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.MatchStatus;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import org.springframework.stereotype.Component;

/**
 * Baut die Board-Embeds (F7). Ein Embed = ein Tages-Slot (FR-023/024). Pro Spiel:
 * Begegnung, Countdown bzw. Live-/Endstand, TV-Sender, Quoten.
 */
@Component
public class BoardEmbed {

    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", Locale.GERMANY);

    private final TimeFormatting time;

    public BoardEmbed(TimeFormatting time) {
        this.time = time;
    }

    public MessageEmbed buildDay(LocalDate date, List<Match> matches) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(new Color(0x2ECC71))
                .setTitle("📅 " + DAY.format(date));
        if (matches.isEmpty()) {
            embed.setDescription("Keine Spiele an diesem Tag.");
        } else {
            embed.setDescription(body(matches));
        }
        embed.setFooter("Aktualisiert");
        return embed.build();
    }

    public MessageEmbed buildFiltered(String title, List<Match> matches) {
        EmbedBuilder embed = new EmbedBuilder().setColor(new Color(0x2ECC71)).setTitle(title);
        embed.setDescription(matches.isEmpty() ? "Keine Spiele." : body(matches));
        return embed.build();
    }

    private String body(List<Match> matches) {
        StringBuilder sb = new StringBuilder();
        for (Match m : matches) {
            sb.append("**").append(m.home()).append(" vs ").append(m.away()).append("**\n")
                    .append(scoreOrCountdown(m));
            if (m.channel() != null && !m.channel().isBlank()) {
                sb.append("  📺 ").append(m.channel());
            }
            if (m.oddsHome() != null && m.oddsDraw() != null && m.oddsAway() != null) {
                sb.append("  💰 ")
                        .append(m.oddsHome().stripTrailingZeros().toPlainString()).append('/')
                        .append(m.oddsDraw().stripTrailingZeros().toPlainString()).append('/')
                        .append(m.oddsAway().stripTrailingZeros().toPlainString());
            }
            sb.append("\n\n");
        }
        return sb.toString();
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
