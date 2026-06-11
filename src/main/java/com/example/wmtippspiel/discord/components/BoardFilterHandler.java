package com.example.wmtippspiel.discord.components;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.example.wmtippspiel.discord.render.BoardEmbed;
import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.persistence.MatchRepository;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;

import org.springframework.stereotype.Component;

/**
 * Beantwortet eine Filter-Auswahl der Board-Navigation mit einer ausschließlich
 * für die auswählende Person sichtbaren (ephemeral) Ansicht; das öffentliche
 * Board bleibt unverändert (FR-025/026, SC-007).
 */
@Component
public class BoardFilterHandler {

    private final MatchRepository matches;
    private final BoardEmbed boardEmbed;
    private final Clock clock;
    private final ZoneId displayZone;

    public BoardFilterHandler(MatchRepository matches, BoardEmbed boardEmbed, Clock clock, ZoneId displayZone) {
        this.matches = matches;
        this.boardEmbed = boardEmbed;
        this.clock = clock;
        this.displayZone = displayZone;
    }

    public void handle(StringSelectInteractionEvent event) {
        String value = event.getValues().isEmpty() ? "" : event.getValues().get(0);
        MessageEmbed embed = render(value);
        event.replyEmbeds(embed).setEphemeral(true).queue();
    }

    private MessageEmbed render(String value) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), displayZone);
        if (value.equals("today")) {
            return dayEmbed("Heute", today);
        }
        if (value.equals("tomorrow")) {
            return dayEmbed("Morgen", today.plusDays(1));
        }
        if (value.equals("ko")) {
            return boardEmbed.buildFiltered("K.o.-Runde", matches.findKnockout());
        }
        if (value.startsWith("group:")) {
            String group = value.substring("group:".length());
            return boardEmbed.buildFiltered("Gruppe " + group, matches.findByGroupLabel(group));
        }
        return boardEmbed.buildFiltered("Spiele", List.of());
    }

    private MessageEmbed dayEmbed(String title, LocalDate date) {
        Instant from = date.atStartOfDay(displayZone).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(displayZone).toInstant();
        return boardEmbed.buildFiltered(title, matches.findBetween(from, to));
    }
}
