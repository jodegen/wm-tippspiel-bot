package com.example.wmtippspiel.discord.commands;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import com.example.wmtippspiel.discord.render.TimeFormatting;
import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.Tip;
import com.example.wmtippspiel.persistence.MatchRepository;

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

import org.springframework.stereotype.Component;

/**
 * Geführter Tipp-Flow (US1 / F3): {@code /tippen} → ephemerales Dropdown der
 * tippbaren Spiele → Auswahl öffnet ein Eingabe-Popup (Modal) mit Heim/Gast
 * (vorbefüllt mit einem bestehenden Tipp) → Absenden speichert und bestätigt
 * ephemeral. Intuitiver als der Slash-Command mit Argumenten.
 */
@Component
public class TippenFlow {

    public static final String COMMAND = "tippen";
    public static final String SELECT_ID = "tipp:select";
    public static final String MODAL_PREFIX = "tipp:modal:";
    private static final int MAX_OPTIONS = 25;

    private final MatchRepository matches;
    private final TipService tipService;
    private final TimeFormatting time;
    private final Clock clock;

    public TippenFlow(MatchRepository matches, TipService tipService, TimeFormatting time, Clock clock) {
        this.matches = matches;
        this.tipService = tipService;
        this.time = time;
        this.clock = clock;
    }

    /** Schritt 1: Dropdown der tippbaren Spiele (ephemeral). */
    public void openMenu(SlashCommandInteractionEvent event) {
        List<Match> tippable = matches.findTippable(clock.instant(), MAX_OPTIONS);
        if (tippable.isEmpty()) {
            event.reply("Aktuell sind keine Spiele tippbar.").setEphemeral(true).queue();
            return;
        }
        StringSelectMenu.Builder menu = StringSelectMenu.create(SELECT_ID)
                .setPlaceholder("⚽ Wähle ein Spiel …");
        for (Match m : tippable) {
            menu.addOption(label(m), String.valueOf(m.id()));
        }
        event.reply("Welches Spiel möchtest du tippen?")
                .addComponents(ActionRow.of(menu.build()))
                .setEphemeral(true)
                .queue();
    }

    /** Schritt 2: Auswahl öffnet das Eingabe-Popup (Modal), vorbefüllt. */
    public void openModal(StringSelectInteractionEvent event) {
        long matchId = Long.parseLong(event.getValues().get(0));
        Optional<Match> match = matches.findById(matchId);
        if (match.isEmpty() || !match.get().isTippable(clock.instant())) {
            event.reply("⏰ Dieses Spiel ist nicht mehr tippbar.").setEphemeral(true).queue();
            return;
        }
        Match m = match.get();
        Optional<Tip> existing = tipService.existingTip(event.getUser().getId(), matchId);

        TextInput home = TextInput.create("heim", "Tore " + m.home(), TextInputStyle.SHORT)
                .setPlaceholder("z. B. 2").setRequiredRange(1, 2).setRequired(true)
                .setValue(existing.map(t -> String.valueOf(t.homeScore())).orElse(null))
                .build();
        TextInput away = TextInput.create("gast", "Tore " + m.away(), TextInputStyle.SHORT)
                .setPlaceholder("z. B. 1").setRequiredRange(1, 2).setRequired(true)
                .setValue(existing.map(t -> String.valueOf(t.awayScore())).orElse(null))
                .build();

        Modal modal = Modal.create(MODAL_PREFIX + matchId, title(m))
                .addComponents(ActionRow.of(home), ActionRow.of(away))
                .build();
        event.replyModal(modal).queue();
    }

    /** Schritt 3: Modal-Absenden validieren, speichern, bestätigen. */
    public void submit(ModalInteractionEvent event) {
        long matchId = Long.parseLong(event.getModalId().substring(MODAL_PREFIX.length()));
        Integer homeTip = parseScore(event.getValue("heim") != null ? event.getValue("heim").getAsString() : null);
        Integer awayTip = parseScore(event.getValue("gast") != null ? event.getValue("gast").getAsString() : null);
        if (homeTip == null || awayTip == null) {
            event.reply("⚠️ Bitte gib für Heim und Gast eine ganze Zahl ≥ 0 ein.").setEphemeral(true).queue();
            return;
        }
        String username = event.getMember() != null
                ? event.getMember().getEffectiveName()
                : event.getUser().getName();
        TipService.Outcome outcome = tipService.submit(event.getUser().getId(), username, matchId, homeTip, awayTip);
        event.reply(TippCommand.message(outcome, homeTip, awayTip)).setEphemeral(true).queue();
    }

    private String label(Match m) {
        String label = m.home() + " vs " + m.away() + " — " + time.human(m.kickoff());
        return label.length() > 100 ? label.substring(0, 100) : label;
    }

    private String title(Match m) {
        String title = "Tipp: " + m.home() + " vs " + m.away();
        return title.length() > 45 ? title.substring(0, 45) : title;
    }

    private static Integer parseScore(String value) {
        if (value == null) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
