package com.example.wmtippspiel.discord.commands;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.Tip;
import com.example.wmtippspiel.persistence.MatchRepository;
import com.example.wmtippspiel.persistence.TipRepository;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Behandelt {@code /tipp <spiel> <heim> <gast>} (US1 / F3).
 *
 * <p>Antwort stets ephemeral (FR-008); Upsert auf {@code (user, match)}
 * (FR-006); Ablehnung, sobald das Spiel nicht (mehr) tippbar ist (FR-007/009).
 */
@Component
public class TippCommand {

    public static final String NAME = "tipp";

    private static final Logger log = LoggerFactory.getLogger(TippCommand.class);

    private final MatchRepository matches;
    private final TipRepository tips;
    private final Clock clock;

    public TippCommand(MatchRepository matches, TipRepository tips, Clock clock) {
        this.matches = matches;
        this.tips = tips;
        this.clock = clock;
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

        Optional<Match> match = matches.findById(matchId);
        Instant now = clock.instant();
        if (match.isEmpty() || !match.get().isTippable(now)) {
            event.reply("Für dieses Spiel kann kein Tipp (mehr) abgegeben werden – die Frist ist vorbei "
                    + "oder das Spiel ist nicht tippbar.").setEphemeral(true).queue();
            return;
        }

        Match m = match.get();
        String username = event.getMember() != null
                ? event.getMember().getEffectiveName()
                : event.getUser().getName();

        tips.upsert(new Tip(event.getUser().getId(), m.id(), username, homeTip, awayTip, now, 0));
        log.info("Tipp gespeichert: user={} match={} {}:{}", event.getUser().getId(), m.id(), homeTip, awayTip);

        event.reply("✅ Dein Tipp für **" + m.home() + " vs " + m.away() + "**: **"
                + homeTip + ":" + awayTip + "** wurde gespeichert.").setEphemeral(true).queue();
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
