package com.example.wmtippspiel.discord.render;

import java.awt.Color;
import java.util.Comparator;
import java.util.List;

import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.evaluation.ScoredTip;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import org.springframework.stereotype.Component;

/** Baut das Auswertungs-Embed (Endstand + Punkte je Tipp); kennzeichnet Korrekturen (F5/FR-017a). */
@Component
public class EvaluationEmbed {

    public MessageEmbed build(Match match, List<ScoredTip> scoredTips, boolean correction) {
        String prefix = correction ? "♻️ Korrektur – Neubewertung: " : "🏁 Auswertung: ";
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(correction ? new Color(0xFEE75C) : new Color(0x57F287))
                .setTitle(prefix + match.home() + " " + match.homeScore() + ":" + match.awayScore() + " " + match.away());

        if (scoredTips.isEmpty()) {
            embed.setDescription("Keine Tipps zu werten.");
            return embed.build();
        }

        StringBuilder sb = new StringBuilder();
        scoredTips.stream()
                .sorted(Comparator.comparingInt(ScoredTip::points).reversed()
                        .thenComparing(ScoredTip::username))
                .forEach(s -> sb.append(pointsLabel(s.points())).append(" **")
                        .append(s.username()).append("**: ")
                        .append(s.homeTip()).append(":").append(s.awayTip())
                        .append("  (+").append(s.points()).append(")\n"));
        embed.setDescription(sb.toString());
        return embed.build();
    }

    private String pointsLabel(int points) {
        return switch (points) {
            case 4 -> "🎯";
            case 3 -> "🔥";
            case 2 -> "✅";
            default -> "❌";
        };
    }
}
