package com.example.wmtippspiel.discord.board;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import com.example.wmtippspiel.config.AppProperties;
import com.example.wmtippspiel.discord.components.BoardNavigation;
import com.example.wmtippspiel.discord.render.BoardEmbed;
import com.example.wmtippspiel.domain.model.BotMessage;
import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.persistence.BotMessageRepository;
import com.example.wmtippspiel.persistence.MatchRepository;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
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
 * Hält das konsolidierte Live-Board aktuell (F7-Redesign): <b>genau eine</b> via
 * {@code bot_messages} getrackte Nachricht ({@code board:main}), die per
 * <b>Edit</b> aktualisiert wird (ortsfest) und die Filter-Komponente direkt
 * mitträgt. Fehlt die Nachricht (Discord 404), wird sie neu gepostet
 * (FR-006/007/008). Das Board zeigt die nächsten {@value #UPCOMING_LIMIT}
 * anstehenden Spiele (Anstoßzeit in der Zukunft, FR-002).
 *
 * <p>Beim Start (ApplicationReadyEvent) entfernt {@link #onStartup()} verwaiste
 * eigene Bot-Nachrichten im Board-Channel (alle außer {@code board:main}),
 * begrenzt auf die letzten {@value #CLEANUP_SCAN} Nachrichten (FR-016/018/019/021).
 */
@Service
public class BoardService {

    private static final Logger log = LoggerFactory.getLogger(BoardService.class);

    /** Einziger Board-Slot (löst die früheren Tages-/Nav-Slots ab). */
    static final String BOARD_KEY = "board:main";
    /** Anzahl der angezeigten anstehenden Spiele. */
    static final int UPCOMING_LIMIT = 12;
    /** Tiefe des Start-Cleanups (eine Discord-History-Seite). */
    static final int CLEANUP_SCAN = 100;

    private final JDA jda;
    private final String boardChannelId;
    private final BotMessageRepository botMessages;
    private final MatchRepository matches;
    private final BoardEmbed boardEmbed;
    private final BoardNavigation navigation;
    private final Clock clock;

    public BoardService(JDA jda,
                        AppProperties properties,
                        BotMessageRepository botMessages,
                        MatchRepository matches,
                        BoardEmbed boardEmbed,
                        BoardNavigation navigation,
                        Clock clock) {
        this.jda = jda;
        this.boardChannelId = properties.discord().boardChannelId();
        this.botMessages = botMessages;
        this.matches = matches;
        this.boardEmbed = boardEmbed;
        this.navigation = navigation;
        this.clock = clock;
    }

    /**
     * Start-Cleanup: verwaiste eigene Bot-Nachrichten im Board-Channel entfernen,
     * anschließend das Board erstmalig aufbauen/aktualisieren. Bricht den Start nie ab.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() throws InterruptedException {
        TextChannel channel = resolveChannel();
        if (channel == null) {
            return;
        }
        jda.awaitReady();
        cleanupOrphans(channel);
        refresh();
    }

    /** Aktualisiert die eine Board-Nachricht (vom {@code boardRefresh}-Job aufgerufen). */
    public void refresh() {
        TextChannel channel = resolveChannel();
        if (channel == null) {
            return;
        }
        List<Match> upcoming = matches.findUpcoming(clock.instant(), UPCOMING_LIMIT);
        log.info("Board-Refresh: {} anstehende Spiele", upcoming.size());
        editOrPostBoard(channel, boardEmbed.buildBoard(upcoming));
    }

    /** Löscht eigene Nachrichten (außer {@code board:main}) in den letzten {@value #CLEANUP_SCAN}. */
    private void cleanupOrphans(TextChannel channel) {
        String keepId = botMessages.findByKey(BOARD_KEY).map(BotMessage::messageId).orElse(null);
        long selfId = jda.getSelfUser().getIdLong();
        List<Message> history;
        try {
            history = channel.getHistory().retrievePast(CLEANUP_SCAN).complete();
        } catch (Exception e) {
            log.warn("Board-Cleanup: Historie konnte nicht gelesen werden ({}) – übersprungen", e.getMessage());
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
        log.info("Board-Cleanup: {} verwaiste Bot-Nachricht(en) entfernt (von {} geprüft)",
                removed, history.size());
    }

    private void editOrPostBoard(TextChannel channel, MessageEmbed embed) {
        Optional<BotMessage> existing = botMessages.findByKey(BOARD_KEY);
        if (existing.isPresent()) {
            String messageId = existing.get().messageId();
            channel.editMessageEmbedsById(messageId, embed)
                    .setComponents(navigation.actionRow())
                    .queue(
                            ok -> { },
                            err -> {
                                if (isUnknownMessage(err)) {
                                    log.info("Board-Nachricht {} fehlt – wird neu gepostet", BOARD_KEY);
                                    postBoard(channel, embed);
                                } else {
                                    log.warn("Board-Edit fehlgeschlagen: {}", err.getMessage());
                                }
                            });
        } else {
            postBoard(channel, embed);
        }
    }

    private void postBoard(TextChannel channel, MessageEmbed embed) {
        channel.sendMessageEmbeds(embed)
                .setComponents(navigation.actionRow())
                .queue(
                        msg -> botMessages.upsert(
                                new BotMessage(BOARD_KEY, channel.getId(), msg.getId(), clock.instant())),
                        err -> log.warn("Board-Post fehlgeschlagen (Rechte im Channel? View/Send/Embed Links): {}",
                                err.getMessage()));
    }

    /** Löst den Board-Channel auf; loggt eine Warnung und liefert {@code null}, wenn nicht möglich. */
    private TextChannel resolveChannel() {
        if (boardChannelId == null || boardChannelId.isBlank()) {
            log.warn("Kein Board-Channel konfiguriert (DISCORD_BOARD_CHANNEL_ID) – Board inaktiv");
            return null;
        }
        TextChannel channel = jda.getTextChannelById(boardChannelId);
        if (channel == null) {
            log.warn("Board-Channel {} nicht gefunden – ist der Bot im Server, ist es ein Textkanal "
                    + "(kein Ankündigungs-/Forum-Kanal) und hat er dort Leserechte?", boardChannelId);
        }
        return channel;
    }

    /**
     * Cleanup-Prädikat (FR-016/018/019): nur eigene Nachrichten löschen und die
     * aktuell gültige {@code board:main}-Nachricht stets verschonen. {@code keepId}
     * ist {@code null}, wenn noch kein Board getrackt ist (Erststart).
     */
    static boolean shouldDelete(long selfId, long authorId, String messageId, String keepId) {
        boolean own = authorId == selfId;
        boolean isBoard = messageId.equals(keepId);
        return own && !isBoard;
    }

    private static boolean isUnknownMessage(Throwable err) {
        return err instanceof ErrorResponseException ere
                && ere.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE;
    }
}
