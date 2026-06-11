package com.example.wmtippspiel.discord.info;

import java.time.Clock;
import java.util.Optional;

import com.example.wmtippspiel.config.AppProperties;
import com.example.wmtippspiel.discord.render.InfoEmbed;
import com.example.wmtippspiel.domain.model.BotMessage;
import com.example.wmtippspiel.persistence.BotMessageRepository;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Pflegt genau ein Info-/Anleitungs-Embed im Info-Channel. Beim Start wird die
 * getrackte Nachricht editiert (oder neu gepostet, falls sie fehlt), sodass
 * dauerhaft nur ein Embed im Channel steht (Edit-in-place + 404-Recovery,
 * analog zum Board).
 */
@Service
public class InfoChannelService {

    private static final Logger log = LoggerFactory.getLogger(InfoChannelService.class);
    private static final String INFO_KEY = "info:guide";

    private final JDA jda;
    private final String infoChannelId;
    private final BotMessageRepository botMessages;
    private final InfoEmbed infoEmbed;
    private final Clock clock;

    public InfoChannelService(JDA jda,
                              AppProperties properties,
                              BotMessageRepository botMessages,
                              InfoEmbed infoEmbed,
                              Clock clock) {
        this.jda = jda;
        this.infoChannelId = properties.discord().infoChannelId();
        this.botMessages = botMessages;
        this.infoEmbed = infoEmbed;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void publishOnStartup() throws InterruptedException {
        if (infoChannelId == null || infoChannelId.isBlank()) {
            log.info("Kein Info-Channel konfiguriert (DISCORD_INFO_CHANNEL_ID) – Info-Embed übersprungen");
            return;
        }
        jda.awaitReady();
        TextChannel channel = jda.getTextChannelById(infoChannelId);
        if (channel == null) {
            log.warn("Info-Channel {} nicht gefunden – ist der Bot im Server, ist es ein Textkanal "
                    + "und hat er dort Schreibrechte?", infoChannelId);
            return;
        }
        publish(channel, infoEmbed.build());
    }

    private void publish(TextChannel channel, MessageEmbed embed) {
        Optional<BotMessage> existing = botMessages.findByKey(INFO_KEY);
        if (existing.isPresent()) {
            channel.editMessageEmbedsById(existing.get().messageId(), embed).queue(
                    ok -> log.info("Info-Embed aktualisiert"),
                    err -> {
                        if (isUnknownMessage(err)) {
                            post(channel, embed);
                        } else {
                            log.warn("Info-Embed-Edit fehlgeschlagen: {}", err.getMessage());
                        }
                    });
        } else {
            post(channel, embed);
        }
    }

    private void post(TextChannel channel, MessageEmbed embed) {
        channel.sendMessageEmbeds(embed).queue(
                msg -> {
                    botMessages.upsert(new BotMessage(INFO_KEY, channel.getId(), msg.getId(), clock.instant()));
                    log.info("Info-Embed gepostet");
                },
                err -> log.warn("Info-Embed-Post fehlgeschlagen (Rechte im Channel? Send Messages/Embed Links): {}",
                        err.getMessage()));
    }

    private static boolean isUnknownMessage(Throwable err) {
        return err instanceof ErrorResponseException ere
                && ere.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE;
    }
}
