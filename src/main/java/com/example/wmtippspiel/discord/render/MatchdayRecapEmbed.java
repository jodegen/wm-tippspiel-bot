package com.example.wmtippspiel.discord.render;

import java.util.List;
import java.util.stream.Collectors;

import com.example.wmtippspiel.persistence.MatchdayScore;
import com.example.wmtippspiel.persistence.MatchdayTipRow;
import com.example.wmtippspiel.recap.MatchdayRecap;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import org.springframework.stereotype.Component;

/** Baut das Spieltags-Rückblick-Embed (F12) im gemeinsamen {@link EmbedStyle}. */
@Component
public class MatchdayRecapEmbed {

    /** Defensive Obergrenze für die Nuller-Liste. */
    static final int MAX_EMPTY_NAMES = 20;

    private final EmbedStyle style;

    public MatchdayRecapEmbed(EmbedStyle style) {
        this.style = style;
    }

    public MessageEmbed build(MatchdayRecap recap) {
        EmbedBuilder embed = style.base("🔁  Spieltags-Rückblick · " + recap.label());

        embed.addField("🥇 Top-Punktesammler", topScorers(recap.topScorers()), false);

        recap.bestTip().ifPresent(t ->
                embed.addField("🎯 Bester Tipp", bestTip(t), false));

        embed.addField("🫥 Leer ausgegangen", emptyHanded(recap.emptyHanded()), false);
        return embed.build();
    }

    private String topScorers(List<MatchdayScore> scorers) {
        if (scorers.isEmpty()) {
            return "Keine Punkte an diesem Spieltag.";
        }
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (MatchdayScore s : scorers) {
            if (shown >= 3) {
                break;
            }
            sb.append(shown == 0 ? "👑 " : "• ")
                    .append(s.username()).append(" — **").append(s.points()).append("** Pkt\n");
            shown++;
        }
        return sb.toString();
    }

    private String bestTip(MatchdayTipRow t) {
        return "**" + t.username() + "** tippte **" + t.tipHome() + ":" + t.tipAway() + "** auf "
                + t.home() + " vs " + t.away() + " (" + t.points() + " Pkt)";
    }

    private String emptyHanded(List<String> names) {
        if (names.isEmpty()) {
            return "Niemand – alle haben gepunktet. 🎉";
        }
        String joined = names.stream().limit(MAX_EMPTY_NAMES).collect(Collectors.joining(", "));
        if (names.size() > MAX_EMPTY_NAMES) {
            joined += " … und " + (names.size() - MAX_EMPTY_NAMES) + " weitere";
        }
        return joined;
    }
}
