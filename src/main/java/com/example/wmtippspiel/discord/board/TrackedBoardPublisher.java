package com.example.wmtippspiel.discord.board;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.example.wmtippspiel.domain.model.BotMessage;
import com.example.wmtippspiel.persistence.BotMessageRepository;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.requests.ErrorResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Wiederverwendbare Board-Mechanik (aus dem F7-Spielplan-Board extrahiert):
 * hält je {@code bot_messages}-Slot <b>genau eine</b> Nachricht per <b>Edit</b>
 * aktuell statt sie neu zu posten, postet bei Discord-404 ({@code UNKNOWN_MESSAGE})
 * neu (Recovery) und entfernt beim Start verwaiste eigene Bot-Nachrichten
 * (Cleanup), ohne fremde Nachrichten oder die gültige getrackte Nachricht zu
 * berühren. Genutzt von {@link BoardService} (Slot {@code board:main}, F7) und
 * dem Leaderboard-Board (Slot {@code board:leaderboard}, F11).
 */
@Component
public class TrackedBoardPublisher {

    private static final Logger log = LoggerFactory.getLogger(TrackedBoardPublisher.class);

    /** Tiefe des Start-Cleanups (eine Discord-History-Seite). */
    static final int CLEANUP_SCAN = 100;

    private final JDA jda;
    private final BotMessageRepository botMessages;
    private final Clock clock;

    public TrackedBoardPublisher(JDA jda, BotMessageRepository botMessages, Clock clock) {
        this.jda = jda;
        this.botMessages = botMessages;
        this.clock = clock;
    }

    /**
     * Editiert die getrackte Nachricht des Slots; fehlt sie (404), wird sie neu
     * gepostet und {@code bot_messages} aktualisiert. {@code components} darf leer
     * sein (löscht vorhandene Komponenten).
     */
    public void editOrPost(TextChannel channel, String key, MessageEmbed embed,
                           Collection<? extends LayoutComponent> components) {
        Optional<BotMessage> existing = botMessages.findByKey(key);
        if (existing.isPresent()) {
            String messageId = existing.get().messageId();
            channel.editMessageEmbedsById(messageId, embed)
                    .setComponents(components)
                    .queue(
                            ok -> { },
                            err -> {
                                if (isUnknownMessage(err)) {
                                    log.info("Board-Nachricht {} fehlt – wird neu gepostet", key);
                                    post(channel, key, embed, components);
                                } else {
                                    log.warn("Board-Edit ({}) fehlgeschlagen: {}", key, err.getMessage());
                                }
                            });
        } else {
            post(channel, key, embed, components);
        }
    }

    private void post(TextChannel channel, String key, MessageEmbed embed,
                      Collection<? extends LayoutComponent> components) {
        channel.sendMessageEmbeds(embed)
                .setComponents(components)
                .queue(
                        msg -> botMessages.upsert(
                                new BotMessage(key, channel.getId(), msg.getId(), clock.instant())),
                        err -> log.warn("Board-Post ({}) fehlgeschlagen (Rechte im Channel?): {}",
                                key, err.getMessage()));
    }

    /**
     * Entfernt eigene, nicht mehr getrackte Bot-Nachrichten in den letzten
     * {@value #CLEANUP_SCAN} Nachrichten des Channels; verschont die aktuell
     * gültige Nachricht des Slots {@code keepKey} und alle fremden Nachrichten.
     */
    public void cleanupOrphans(TextChannel channel, String keepKey) {
        String keepId = botMessages.findByKey(keepKey).map(BotMessage::messageId).orElse(null);
        long selfId = jda.getSelfUser().getIdLong();
        List<Message> history;
        try {
            history = channel.getHistory().retrievePast(CLEANUP_SCAN).complete();
        } catch (Exception e) {
            log.warn("Board-Cleanup ({}): Historie nicht lesbar ({}) – übersprungen", keepKey, e.getMessage());
            return;
        }
        int removed = 0;
        for (Message msg : history) {
            if (shouldDelete(selfId, msg.getAuthor().getIdLong(), msg.getId(), keepId)) {
                msg.delete().queue(
                        ok -> { },
                        err -> log.warn("Board-Cleanup: Löschen von {} fehlgeschlagen: {}",
                                msg.getId(), err.getMessage()));
                removed++;
            }
        }
        log.info("Board-Cleanup ({}): {} verwaiste Bot-Nachricht(en) entfernt (von {} geprüft)",
                keepKey, removed, history.size());
    }

    /**
     * Cleanup-Prädikat: nur eigene Nachrichten löschen und die aktuell gültige
     * getrackte Nachricht stets verschonen. {@code keepId} ist {@code null}, wenn
     * noch nichts getrackt ist (Erststart).
     */
    public static boolean shouldDelete(long selfId, long authorId, String messageId, String keepId) {
        return authorId == selfId && !messageId.equals(keepId);
    }

    private static boolean isUnknownMessage(Throwable err) {
        return err instanceof ErrorResponseException ere
                && ere.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE;
    }
}
