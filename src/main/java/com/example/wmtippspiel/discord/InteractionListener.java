package com.example.wmtippspiel.discord;

import com.example.wmtippspiel.discord.commands.NaechstesCommand;
import com.example.wmtippspiel.discord.commands.RanglisteCommand;
import com.example.wmtippspiel.discord.commands.SpielplanCommand;
import com.example.wmtippspiel.discord.commands.TippAutocomplete;
import com.example.wmtippspiel.discord.commands.TippCommand;
import com.example.wmtippspiel.discord.components.BoardFilterHandler;
import com.example.wmtippspiel.discord.components.BoardNavigation;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import org.springframework.stereotype.Component;

/**
 * Zentraler Gateway-Listener: routet Slash-Commands, Autocomplete- und
 * Component-Interaktionen an die jeweiligen Handler (ereignisgetrieben,
 * Verfassung Prinzip V / FR-028).
 */
@Component
public class InteractionListener extends ListenerAdapter {

    private final TippCommand tippCommand;
    private final TippAutocomplete tippAutocomplete;
    private final RanglisteCommand ranglisteCommand;
    private final SpielplanCommand spielplanCommand;
    private final NaechstesCommand naechstesCommand;
    private final BoardFilterHandler boardFilterHandler;

    public InteractionListener(TippCommand tippCommand,
                               TippAutocomplete tippAutocomplete,
                               RanglisteCommand ranglisteCommand,
                               SpielplanCommand spielplanCommand,
                               NaechstesCommand naechstesCommand,
                               BoardFilterHandler boardFilterHandler) {
        this.tippCommand = tippCommand;
        this.tippAutocomplete = tippAutocomplete;
        this.ranglisteCommand = ranglisteCommand;
        this.spielplanCommand = spielplanCommand;
        this.naechstesCommand = naechstesCommand;
        this.boardFilterHandler = boardFilterHandler;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case TippCommand.NAME -> tippCommand.handle(event);
            case RanglisteCommand.NAME -> ranglisteCommand.handle(event);
            case SpielplanCommand.NAME -> spielplanCommand.handle(event);
            case NaechstesCommand.NAME -> naechstesCommand.handle(event);
            default -> { /* unbekannter Command – ignorieren */ }
        }
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (TippCommand.NAME.equals(event.getName())
                && "spiel".equals(event.getFocusedOption().getName())) {
            tippAutocomplete.handle(event);
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (BoardNavigation.FILTER_ID.equals(event.getComponentId())) {
            boardFilterHandler.handle(event);
        }
    }
}
