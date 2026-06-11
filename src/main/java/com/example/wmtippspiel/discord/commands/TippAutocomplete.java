package com.example.wmtippspiel.discord.commands;

import java.time.Clock;
import java.util.List;

import com.example.wmtippspiel.discord.render.TimeFormatting;
import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.persistence.MatchRepository;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;

import org.springframework.stereotype.Component;

/**
 * Autocomplete für die Option {@code spiel} von {@code /tipp}: bietet nur
 * tippbare Spiele an (Zukunft, Teams bekannt, nicht abgesagt; FR-009), Value =
 * Match-ID. Discord erlaubt maximal 25 Choices.
 */
@Component
public class TippAutocomplete {

    private static final int MAX_CHOICES = 25;

    private final MatchRepository matches;
    private final TimeFormatting time;
    private final Clock clock;

    public TippAutocomplete(MatchRepository matches, TimeFormatting time, Clock clock) {
        this.matches = matches;
        this.time = time;
        this.clock = clock;
    }

    public void handle(CommandAutoCompleteInteractionEvent event) {
        List<Match> tippable = matches.findTippable(clock.instant(), MAX_CHOICES);
        List<Command.Choice> choices = tippable.stream()
                .map(m -> new Command.Choice(label(m), String.valueOf(m.id())))
                .toList();
        event.replyChoices(choices).queue();
    }

    private String label(Match m) {
        String label = m.home() + " vs " + m.away() + " — " + time.human(m.kickoff());
        return label.length() > 100 ? label.substring(0, 100) : label;
    }
}
