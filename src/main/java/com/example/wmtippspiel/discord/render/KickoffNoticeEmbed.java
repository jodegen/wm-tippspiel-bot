package com.example.wmtippspiel.discord.render;

import java.awt.Color;

import com.example.wmtippspiel.domain.model.Match;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import org.springframework.stereotype.Component;

/** Embed für den "Anpfiff steht bevor"-Hinweis kurz vor Spielbeginn. */
@Component
public class KickoffNoticeEmbed {

    private final TimeFormatting time;

    public KickoffNoticeEmbed(TimeFormatting time) {
        this.time = time;
    }

    public MessageEmbed build(Match match) {
        StringBuilder desc = new StringBuilder()
                .append("Anpfiff ").append(time.relative(match.kickoff()))
                .append(" (").append(time.full(match.kickoff())).append(")");
        if (match.channel() != null && !match.channel().isBlank()) {
            desc.append("\n📺 ").append(match.channel());
        }
        return new EmbedBuilder()
                .setColor(new Color(0xE67E22))
                .setTitle("⏰ Gleich geht's los: " + match.home() + " vs " + match.away())
                .setDescription(desc.toString())
                .build();
    }
}
