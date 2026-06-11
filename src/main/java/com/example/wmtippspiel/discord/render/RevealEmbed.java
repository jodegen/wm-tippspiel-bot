package com.example.wmtippspiel.discord.render;

import java.awt.Color;
import java.util.List;

import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.Tip;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import org.springframework.stereotype.Component;

/** Baut das Offenlegungs-Embed mit allen abgegebenen Tipps eines Spiels (F4). */
@Component
public class RevealEmbed {

    public MessageEmbed build(Match match, List<Tip> tips) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(new Color(0x5865F2))
                .setTitle("🔓 Tipps offengelegt: " + match.home() + " vs " + match.away());

        if (tips.isEmpty()) {
            embed.setDescription("Für dieses Spiel wurden keine Tipps abgegeben.");
        } else {
            StringBuilder sb = new StringBuilder();
            for (Tip tip : tips) {
                sb.append("**").append(tip.username()).append("**: ")
                        .append(tip.homeScore()).append(":").append(tip.awayScore()).append('\n');
            }
            embed.setDescription(sb.toString());
            embed.setFooter(tips.size() + " Tipp(s)");
        }
        return embed.build();
    }
}
