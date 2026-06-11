package com.example.wmtippspiel.discord.commands;

import java.time.Clock;
import java.util.List;

import com.example.wmtippspiel.discord.render.SpielplanEmbed;
import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.persistence.MatchRepository;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import org.springframework.stereotype.Component;

/** Behandelt {@code /naechstes} (US6 / F2) – das unmittelbar nächste Spiel. */
@Component
public class NaechstesCommand {

    public static final String NAME = "naechstes";

    private final MatchRepository matches;
    private final SpielplanEmbed embed;
    private final Clock clock;

    public NaechstesCommand(MatchRepository matches, SpielplanEmbed embed, Clock clock) {
        this.matches = matches;
        this.embed = embed;
        this.clock = clock;
    }

    public void handle(SlashCommandInteractionEvent event) {
        List<Match> next = matches.findUpcoming(clock.instant(), 1);
        if (next.isEmpty()) {
            event.replyEmbeds(embed.empty("Aktuell kein anstehendes Spiel.")).queue();
            return;
        }
        event.replyEmbeds(embed.buildNext(next.get(0))).queue();
    }
}
