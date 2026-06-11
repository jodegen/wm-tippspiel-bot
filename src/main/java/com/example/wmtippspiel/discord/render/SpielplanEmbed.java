package com.example.wmtippspiel.discord.render;

import java.awt.Color;
import java.math.BigDecimal;
import java.util.List;

import com.example.wmtippspiel.domain.model.Match;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import org.springframework.stereotype.Component;

/** Embeds für Spielplan-Übersicht (F1) und nächstes Spiel (F2). */
@Component
public class SpielplanEmbed {

    private final TimeFormatting time;

    public SpielplanEmbed(TimeFormatting time) {
        this.time = time;
    }

    public MessageEmbed buildSchedule(List<Match> upcoming) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(new Color(0x3498DB))
                .setTitle("📅 Spielplan – nächste Spiele");
        if (upcoming.isEmpty()) {
            embed.setDescription("Aktuell keine anstehenden Spiele.");
            return embed.build();
        }
        for (Match m : upcoming) {
            embed.addField(m.home() + " vs " + m.away(), fieldValue(m), false);
        }
        return embed.build();
    }

    public MessageEmbed buildNext(Match match) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(new Color(0x3498DB))
                .setTitle("⏭️ Nächstes Spiel: " + match.home() + " vs " + match.away())
                .setDescription("Anpfiff " + time.relative(match.kickoff())
                        + "\n" + time.full(match.kickoff())
                        + channelLine(match) + oddsLine(match));
        return embed.build();
    }

    public MessageEmbed empty(String message) {
        return new EmbedBuilder().setColor(new Color(0x3498DB)).setDescription(message).build();
    }

    private String fieldValue(Match m) {
        return time.full(m.kickoff()) + " (" + time.relative(m.kickoff()) + ")"
                + channelLine(m) + oddsLine(m);
    }

    private String channelLine(Match m) {
        return m.channel() != null && !m.channel().isBlank() ? "\n📺 " + m.channel() : "";
    }

    private String oddsLine(Match m) {
        if (m.oddsHome() == null || m.oddsDraw() == null || m.oddsAway() == null) {
            return "";
        }
        return "\n💰 " + fmt(m.oddsHome()) + " / " + fmt(m.oddsDraw()) + " / " + fmt(m.oddsAway());
    }

    private String fmt(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
