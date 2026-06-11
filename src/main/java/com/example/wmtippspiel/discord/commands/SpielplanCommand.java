package com.example.wmtippspiel.discord.commands;

import java.time.Clock;

import com.example.wmtippspiel.discord.render.SpielplanEmbed;
import com.example.wmtippspiel.persistence.MatchRepository;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import org.springframework.stereotype.Component;

/** Behandelt {@code /spielplan [anzahl]} (US6 / F1). */
@Component
public class SpielplanCommand {

    public static final String NAME = "spielplan";
    public static final String OPTION_ANZAHL = "anzahl";

    private static final int DEFAULT_COUNT = 5;
    private static final int MAX_COUNT = 25;

    private final MatchRepository matches;
    private final SpielplanEmbed embed;
    private final Clock clock;

    public SpielplanCommand(MatchRepository matches, SpielplanEmbed embed, Clock clock) {
        this.matches = matches;
        this.embed = embed;
        this.clock = clock;
    }

    public void handle(SlashCommandInteractionEvent event) {
        int count = DEFAULT_COUNT;
        if (event.getOption(OPTION_ANZAHL) != null) {
            count = (int) Math.max(1, Math.min(MAX_COUNT, event.getOption(OPTION_ANZAHL).getAsLong()));
        }
        event.replyEmbeds(embed.buildSchedule(matches.findUpcoming(clock.instant(), count))).queue();
    }
}
