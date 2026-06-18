package com.example.wmtippspiel.discord.board;

import java.time.Clock;
import java.util.List;

import com.example.wmtippspiel.config.AppProperties;
import com.example.wmtippspiel.discord.components.BoardNavigation;
import com.example.wmtippspiel.discord.render.BoardEmbed;
import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.persistence.MatchRepository;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Hält das konsolidierte Live-Board aktuell (F7-Redesign): <b>genau eine</b> via
 * {@code bot_messages} getrackte Nachricht ({@code board:main}), die per
 * <b>Edit</b> aktualisiert wird (ortsfest) und die Filter-Komponente direkt
 * mitträgt. Die Board-Mechanik (Edit/Recovery/Cleanup) liegt im wiederverwendbaren
 * {@link TrackedBoardPublisher}; das Board zeigt die nächsten
 * {@value #UPCOMING_LIMIT} anstehenden Spiele (FR-002).
 *
 * <p>Beim Start (ApplicationReadyEvent) entfernt {@link #onStartup()} verwaiste
 * eigene Bot-Nachrichten im Board-Channel (alle außer {@code board:main}).
 */
@Service
public class BoardService {

    private static final Logger log = LoggerFactory.getLogger(BoardService.class);

    /** Einziger Spielplan-Board-Slot (löst die früheren Tages-/Nav-Slots ab). */
    static final String BOARD_KEY = "board:main";
    /** Anzahl der angezeigten anstehenden Spiele. */
    static final int UPCOMING_LIMIT = 12;

    private final JDA jda;
    private final String boardChannelId;
    private final MatchRepository matches;
    private final BoardEmbed boardEmbed;
    private final BoardNavigation navigation;
    private final TrackedBoardPublisher publisher;
    private final Clock clock;

    public BoardService(JDA jda,
                        AppProperties properties,
                        MatchRepository matches,
                        BoardEmbed boardEmbed,
                        BoardNavigation navigation,
                        TrackedBoardPublisher publisher,
                        Clock clock) {
        this.jda = jda;
        this.boardChannelId = properties.discord().boardChannelId();
        this.matches = matches;
        this.boardEmbed = boardEmbed;
        this.navigation = navigation;
        this.publisher = publisher;
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
        publisher.cleanupOrphans(channel, BOARD_KEY);
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
        publisher.editOrPost(channel, BOARD_KEY, boardEmbed.buildBoard(upcoming),
                List.of(navigation.actionRow()));
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
     * Cleanup-Prädikat (FR-016/018/019) – delegiert an {@link TrackedBoardPublisher};
     * hier als stabiler Einstieg für die Unit-Tests belassen.
     */
    static boolean shouldDelete(long selfId, long authorId, String messageId, String keepId) {
        return TrackedBoardPublisher.shouldDelete(selfId, authorId, messageId, keepId);
    }
}
