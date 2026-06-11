package com.example.wmtippspiel.discord.board;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Hält das Live-Board aktuell (F7): je Tages-Slot eine via {@code bot_messages}
 * getrackte Nachricht, die per <b>Edit</b> aktualisiert wird (ortsfest,
 * FR-021/022). Fehlt die Nachricht (Discord 404), wird sie neu gepostet und die
 * Position aktualisiert (FR-027). Aufteilung nach Tag hält die Embed-Limits ein
 * (FR-023). Die Navigationskomponente wird einmalig gepostet.
 */
@Service
public class BoardService {

    private static final Logger log = LoggerFactory.getLogger(BoardService.class);
    private static final int DAYS_AHEAD = 3;
    private static final String NAV_KEY = "board:nav";

    private final JDA jda;
    private final String boardChannelId;
    private final BotMessageRepository botMessages;
    private final MatchRepository matches;
    private final BoardEmbed boardEmbed;
    private final BoardNavigation navigation;
    private final Clock clock;
    private final ZoneId displayZone;

    public BoardService(JDA jda,
                        AppProperties properties,
                        BotMessageRepository botMessages,
                        MatchRepository matches,
                        BoardEmbed boardEmbed,
                        BoardNavigation navigation,
                        Clock clock,
                        ZoneId displayZone) {
        this.jda = jda;
        this.boardChannelId = properties.discord().boardChannelId();
        this.botMessages = botMessages;
        this.matches = matches;
        this.boardEmbed = boardEmbed;
        this.navigation = navigation;
        this.clock = clock;
        this.displayZone = displayZone;
    }

    /** Aktualisiert alle Tages-Slots und stellt die Navigationskomponente sicher. */
    public void refresh() {
        if (boardChannelId == null || boardChannelId.isBlank()) {
            log.warn("Kein Board-Channel konfiguriert (DISCORD_BOARD_CHANNEL_ID) – Board wird nicht aktualisiert");
            return;
        }
        TextChannel channel = jda.getTextChannelById(boardChannelId);
        if (channel == null) {
            log.warn("Board-Channel {} nicht gefunden", boardChannelId);
            return;
        }

        LocalDate today = LocalDate.ofInstant(clock.instant(), displayZone);
        for (int offset = 0; offset <= DAYS_AHEAD; offset++) {
            LocalDate date = today.plusDays(offset);
            Instant from = date.atStartOfDay(displayZone).toInstant();
            Instant to = date.plusDays(1).atStartOfDay(displayZone).toInstant();
            List<Match> dayMatches = matches.findBetween(from, to);
            editOrPostEmbed(channel, "board:day:" + date, boardEmbed.buildDay(date, dayMatches));
        }
        ensureNav(channel);
    }

    private void editOrPostEmbed(TextChannel channel, String key, MessageEmbed embed) {
        Optional<BotMessage> existing = botMessages.findByKey(key);
        if (existing.isPresent()) {
            String messageId = existing.get().messageId();
            channel.editMessageEmbedsById(messageId, embed).queue(
                    ok -> { },
                    err -> {
                        if (isUnknownMessage(err)) {
                            log.info("Board-Nachricht {} fehlt – wird neu gepostet", key);
                            postEmbed(channel, key, embed);
                        } else {
                            log.warn("Board-Edit für {} fehlgeschlagen: {}", key, err.getMessage());
                        }
                    });
        } else {
            postEmbed(channel, key, embed);
        }
    }

    private void postEmbed(TextChannel channel, String key, MessageEmbed embed) {
        channel.sendMessageEmbeds(embed).queue(
                msg -> botMessages.upsert(new BotMessage(key, channel.getId(), msg.getId(), clock.instant())),
                err -> log.warn("Board-Post für {} fehlgeschlagen (Rechte im Channel? View/Send/Embed Links): {}",
                        key, err.getMessage()));
    }

    private void ensureNav(TextChannel channel) {
        if (botMessages.findByKey(NAV_KEY).isPresent()) {
            return;
        }
        channel.sendMessageEmbeds(boardEmbed.buildFiltered("🔎 Filter", List.of()))
                .setComponents(navigation.actionRow())
                .queue(
                        msg -> botMessages.upsert(new BotMessage(NAV_KEY, channel.getId(), msg.getId(), clock.instant())),
                        err -> log.warn("Board-Navigation-Post fehlgeschlagen (Rechte im Channel?): {}",
                                err.getMessage()));
    }

    private static boolean isUnknownMessage(Throwable err) {
        return err instanceof ErrorResponseException ere
                && ere.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE;
    }
}
