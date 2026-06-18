package com.example.wmtippspiel.discord;

import com.example.wmtippspiel.discord.commands.NaechstesCommand;
import com.example.wmtippspiel.discord.commands.ProfilCommand;
import com.example.wmtippspiel.discord.commands.RanglisteCommand;
import com.example.wmtippspiel.discord.commands.SpielplanCommand;
import com.example.wmtippspiel.discord.commands.TippAutocomplete;
import com.example.wmtippspiel.discord.commands.TippCommand;
import com.example.wmtippspiel.discord.commands.TippenFlow;
import com.example.wmtippspiel.discord.components.BoardFilterHandler;
import com.example.wmtippspiel.discord.components.BoardNavigation;
import com.example.wmtippspiel.discord.notify.NotifyService;

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import org.springframework.stereotype.Component;

/**
 * Zentraler Gateway-Listener: routet Slash-Commands, Autocomplete-, Select- und
 * Modal-Interaktionen an die jeweiligen Handler (ereignisgetrieben, Verfassung
 * Prinzip V / FR-028).
 */
@Component
public class InteractionListener extends ListenerAdapter {

    private final TippCommand tippCommand;
    private final TippAutocomplete tippAutocomplete;
    private final TippenFlow tippenFlow;
    private final RanglisteCommand ranglisteCommand;
    private final SpielplanCommand spielplanCommand;
    private final NaechstesCommand naechstesCommand;
    private final ProfilCommand profilCommand;
    private final BoardFilterHandler boardFilterHandler;
    private final NotifyService notifyService;

    public InteractionListener(TippCommand tippCommand,
                               TippAutocomplete tippAutocomplete,
                               TippenFlow tippenFlow,
                               RanglisteCommand ranglisteCommand,
                               SpielplanCommand spielplanCommand,
                               NaechstesCommand naechstesCommand,
                               ProfilCommand profilCommand,
                               BoardFilterHandler boardFilterHandler,
                               NotifyService notifyService) {
        this.tippCommand = tippCommand;
        this.tippAutocomplete = tippAutocomplete;
        this.tippenFlow = tippenFlow;
        this.ranglisteCommand = ranglisteCommand;
        this.spielplanCommand = spielplanCommand;
        this.naechstesCommand = naechstesCommand;
        this.profilCommand = profilCommand;
        this.boardFilterHandler = boardFilterHandler;
        this.notifyService = notifyService;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case TippCommand.NAME -> tippCommand.handle(event);
            case TippenFlow.COMMAND -> tippenFlow.openMenu(event);
            case RanglisteCommand.NAME -> ranglisteCommand.handle(event);
            case SpielplanCommand.NAME -> spielplanCommand.handle(event);
            case NaechstesCommand.NAME -> naechstesCommand.handle(event);
            case ProfilCommand.NAME -> profilCommand.handle(event);
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
        String id = event.getComponentId();
        if (TippenFlow.SELECT_ID.equals(id)) {
            tippenFlow.openModal(event);
        } else if (BoardNavigation.FILTER_ID.equals(id)) {
            boardFilterHandler.handle(event);
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        switch (event.getComponentId()) {
            case TippenFlow.START_BUTTON -> tippenFlow.openMenu(event);
            case NotifyService.TOGGLE_BUTTON -> notifyService.toggle(event);
            default -> { /* unbekannter Button – ignorieren */ }
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (event.getModalId().startsWith(TippenFlow.MODAL_PREFIX)) {
            tippenFlow.submit(event);
        }
    }
}
