package com.example.wmtippspiel.discord.tip;

import java.time.Clock;
import java.util.Optional;

import com.example.wmtippspiel.config.AppProperties;
import com.example.wmtippspiel.discord.commands.TippenFlow;
import com.example.wmtippspiel.discord.render.TipCtaEmbed;
import com.example.wmtippspiel.domain.model.BotMessage;
import com.example.wmtippspiel.persistence.BotMessageRepository;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.ErrorResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Pflegt genau eine Nachricht im Tipp-Channel: ein kurzes Embed plus den
 * "⚽ Jetzt tippen"-Button, der den geführten Tipp-Flow startet. Edit-in-place
 * mit 404-Recovery (analog Info-/Board-Channel), gepostet beim Start.
 */
@Service
public class TipChannelService {

    private static final Logger log = LoggerFactory.getLogger(TipChannelService.class);
    private static final String TIP_KEY = "tip:cta";

    private final JDA jda;
    private final String tipChannelId;
    private final BotMessageRepository botMessages;
    private final TipCtaEmbed ctaEmbed;
    private final Clock clock;

    public TipChannelService(JDA jda,
                             AppProperties properties,
                             BotMessageRepository botMessages,
                             TipCtaEmbed ctaEmbed,
                             Clock clock) {
        this.jda = jda;
        this.tipChannelId = properties.discord().tipChannelId();
        this.botMessages = botMessages;
        this.ctaEmbed = ctaEmbed;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void publishOnStartup() throws InterruptedException {
        if (tipChannelId == null || tipChannelId.isBlank()) {
            log.info("Kein Tipp-Channel konfiguriert (DISCORD_TIP_CHANNEL_ID) – Tipp-Button übersprungen");
            return;
        }
        jda.awaitReady();
        TextChannel channel = jda.getTextChannelById(tipChannelId);
        if (channel == null) {
            log.warn("Tipp-Channel {} nicht gefunden – ist der Bot im Server, ist es ein Textkanal "
                    + "und hat er dort Schreibrechte?", tipChannelId);
            return;
        }
        publish(channel, ctaEmbed.build());
    }

    private void publish(TextChannel channel, MessageEmbed embed) {
        ActionRow buttonRow = ActionRow.of(Button.success(TippenFlow.START_BUTTON, "⚽ Jetzt tippen"));
        Optional<BotMessage> existing = botMessages.findByKey(TIP_KEY);
        if (existing.isPresent()) {
            channel.editMessageEmbedsById(existing.get().messageId(), embed)
                    .setComponents(buttonRow)
                    .queue(
                            ok -> log.info("Tipp-Button-Nachricht aktualisiert"),
                            err -> {
                                if (isUnknownMessage(err)) {
                                    post(channel, embed, buttonRow);
                                } else {
                                    log.warn("Tipp-Button-Edit fehlgeschlagen: {}", err.getMessage());
                                }
                            });
        } else {
            post(channel, embed, buttonRow);
        }
    }

    private void post(TextChannel channel, MessageEmbed embed, ActionRow buttonRow) {
        channel.sendMessageEmbeds(embed).setComponents(buttonRow).queue(
                msg -> {
                    botMessages.upsert(new BotMessage(TIP_KEY, channel.getId(), msg.getId(), clock.instant()));
                    log.info("Tipp-Button-Nachricht gepostet");
                },
                err -> log.warn("Tipp-Button-Post fehlgeschlagen (Rechte im Channel?): {}", err.getMessage()));
    }

    private static boolean isUnknownMessage(Throwable err) {
        return err instanceof ErrorResponseException ere
                && ere.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE;
    }
}
