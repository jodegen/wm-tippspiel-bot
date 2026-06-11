package com.example.wmtippspiel.discord.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import org.springframework.stereotype.Component;

/**
 * Behandelt {@code /tipp <spiel> <heim> <gast>} (US1 / F3) als Direktzugriff/
 * Fallback. Antwort stets ephemeral (FR-008); Logik im {@link TipService}.
 */
@Component
public class TippCommand {

    public static final String NAME = "tipp";

    private final TipService tipService;

    public TippCommand(TipService tipService) {
        this.tipService = tipService;
    }

    public void handle(SlashCommandInteractionEvent event) {
        String rawMatch = event.getOption("spiel") != null ? event.getOption("spiel").getAsString() : null;
        Long matchId = parseLong(rawMatch);
        if (matchId == null) {
            event.reply("Ungültige Spielauswahl.").setEphemeral(true).queue();
            return;
        }
        int homeTip = event.getOption("heim").getAsInt();
        int awayTip = event.getOption("gast").getAsInt();
        String username = event.getMember() != null
                ? event.getMember().getEffectiveName()
                : event.getUser().getName();

        TipService.Outcome outcome = tipService.submit(event.getUser().getId(), username, matchId, homeTip, awayTip);
        event.reply(message(outcome, homeTip, awayTip)).setEphemeral(true).queue();
    }

    static String message(TipService.Outcome outcome, int homeTip, int awayTip) {
        return switch (outcome.result()) {
            case OK -> "✅ Dein Tipp für **" + outcome.match().home() + " vs " + outcome.match().away()
                    + "**: **" + homeTip + ":" + awayTip + "** wurde gespeichert.";
            case NOT_TIPPABLE -> "⏰ Für dieses Spiel kann kein Tipp (mehr) abgegeben werden – "
                    + "die Frist ist vorbei oder das Spiel ist nicht tippbar.";
            case NOT_FOUND -> "❓ Spiel nicht gefunden.";
        };
    }

    private static Long parseLong(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
