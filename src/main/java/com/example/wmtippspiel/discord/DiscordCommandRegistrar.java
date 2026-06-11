package com.example.wmtippspiel.discord;

import com.example.wmtippspiel.config.AppProperties;
import com.example.wmtippspiel.discord.commands.NaechstesCommand;
import com.example.wmtippspiel.discord.commands.RanglisteCommand;
import com.example.wmtippspiel.discord.commands.SpielplanCommand;
import com.example.wmtippspiel.discord.commands.TippCommand;
import com.example.wmtippspiel.discord.commands.TippenFlow;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Registriert die Slash-Commands guild-scoped, sobald die Anwendung bereit und
 * die JDA-Gateway-Verbindung aufgebaut ist (sofort verfügbar, kein globaler
 * Propagations-Delay). Weitere Commands kommen in späteren Phasen hinzu.
 */
@Component
public class DiscordCommandRegistrar {

    private static final Logger log = LoggerFactory.getLogger(DiscordCommandRegistrar.class);

    private final JDA jda;
    private final AppProperties properties;

    public DiscordCommandRegistrar(JDA jda, AppProperties properties) {
        this.jda = jda;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerCommands() throws InterruptedException {
        jda.awaitReady();
        String guildId = properties.discord().guildId();
        Guild guild = guildId == null || guildId.isBlank() ? null : jda.getGuildById(guildId);
        if (guild == null) {
            log.warn("Guild {} nicht gefunden – Slash-Commands nicht registriert", guildId);
            return;
        }
        guild.updateCommands().addCommands(
                Commands.slash(TippCommand.NAME, "Tipp für ein Spiel abgeben")
                        .addOptions(
                                new OptionData(OptionType.STRING, "spiel", "Spiel auswählen", true, true),
                                new OptionData(OptionType.INTEGER, "heim", "Tore Heim", true).setMinValue(0),
                                new OptionData(OptionType.INTEGER, "gast", "Tore Gast", true).setMinValue(0)),
                Commands.slash(TippenFlow.COMMAND, "Geführt tippen: Spiel auswählen und Ergebnis eingeben"),
                Commands.slash(RanglisteCommand.NAME, "Aktuelle Rangliste anzeigen"),
                Commands.slash(SpielplanCommand.NAME, "Die nächsten Spiele anzeigen")
                        .addOptions(new OptionData(OptionType.INTEGER, SpielplanCommand.OPTION_ANZAHL,
                                "Anzahl (Standard 5)", false).setMinValue(1).setMaxValue(25)),
                Commands.slash(NaechstesCommand.NAME, "Das nächste Spiel anzeigen")
        ).queue(
                ok -> log.info("Slash-Commands für Guild {} registriert", guild.getId()),
                err -> log.error("Slash-Command-Registrierung fehlgeschlagen", err));
    }
}
