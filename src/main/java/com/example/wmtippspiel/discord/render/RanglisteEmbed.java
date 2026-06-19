package com.example.wmtippspiel.discord.render;

import java.awt.Color;
import java.util.List;

import com.example.wmtippspiel.persistence.LeaderboardEntry;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import org.springframework.stereotype.Component;

/**
 * Baut das Ranglisten-Embed. Bei Gleichstand in Punkten UND exakten Treffern
 * teilen sich Teilnehmer denselben Rang (FR-020).
 *
 * <p>Feature 009: Ist eine {@code leaderboardUrl} übergeben, wird am Ende der
 * (nicht-leeren) Beschreibung eine klickbare Link-Zeile zur vollständigen Web-Tabelle
 * ergänzt (FR-004). Bei {@code null}/leer oder leerer Rangliste wird nichts angehängt
 * (FR-006). Der Linktext ist eine kurze, feste Konstante (FR-010).
 */
@Component
public class RanglisteEmbed {

    public MessageEmbed build(List<LeaderboardEntry> entries, String leaderboardUrl) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(new Color(0xEB459E))
                .setTitle("🏆 Rangliste");

        if (entries.isEmpty()) {
            embed.setDescription("Noch keine Tipps abgegeben.");
            return embed.build();
        }

        StringBuilder sb = new StringBuilder();
        int rank = 0;
        int index = 0;
        LeaderboardEntry previous = null;
        for (LeaderboardEntry e : entries) {
            index++;
            // Standard-Competition-Ranking: gleicher Rang bei gleichen Punkten UND exakten Treffern.
            if (previous == null
                    || e.totalPoints() != previous.totalPoints()
                    || e.exactHits() != previous.exactHits()) {
                rank = index;
            }
            sb.append("**").append(rank).append(".** ")
                    .append(e.username())
                    .append(" — **").append(e.totalPoints()).append("** Pkt")
                    .append(" (").append(e.tipCount()).append(" Tipps, ")
                    .append(e.exactHits()).append("× exakt)\n");
            previous = e;
        }
        if (leaderboardUrl != null && !leaderboardUrl.isBlank()) {
            sb.append("\n🔗 [Vollständige Tabelle ansehen](").append(leaderboardUrl).append(')');
        }
        embed.setDescription(sb.toString());
        return embed.build();
    }
}
