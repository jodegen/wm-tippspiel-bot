package com.example.wmtippspiel.discord.render;

import java.awt.Color;

import com.example.wmtippspiel.live.GoalEvent;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import org.springframework.stereotype.Component;

/** Embeds für Live-Tore (F8): „⚽ TOR!" und „⛔ Tor aberkannt". */
@Component
public class GoalEmbed {

    public MessageEmbed goal(GoalEvent event) {
        String scorer = event.scoringTeam() == GoalEvent.ScoringTeam.HOME ? event.home() : event.away();
        String minute = event.minute() != null ? " — " + event.minute() + "'" : "";
        return new EmbedBuilder()
                .setColor(new Color(0x1ABC9C))
                .setTitle("⚽ TOR! " + event.home() + " " + event.newHome() + ":" + event.newAway()
                        + " " + event.away())
                .setDescription("Treffer für **" + scorer + "**" + minute)
                .build();
    }

    public MessageEmbed correction(GoalEvent event) {
        return new EmbedBuilder()
                .setColor(new Color(0xE74C3C))
                .setTitle("⛔ Tor aberkannt — " + event.home() + " " + event.newHome() + ":"
                        + event.newAway() + " " + event.away())
                .setDescription("Der Spielstand wurde nach unten korrigiert.")
                .build();
    }
}
