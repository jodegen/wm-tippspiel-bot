package com.example.wmtippspiel.leaderboard;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import com.example.wmtippspiel.config.AppProperties;
import com.example.wmtippspiel.discord.board.TrackedBoardPublisher;
import com.example.wmtippspiel.discord.render.LeaderboardBoardEmbed;
import com.example.wmtippspiel.persistence.LeaderboardEntry;
import com.example.wmtippspiel.persistence.LeaderboardSnapshotRepository;
import com.example.wmtippspiel.persistence.TipRepository;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Hält das Live-Leaderboard-Board aktuell (F11): <b>genau eine</b> getrackte
 * Nachricht ({@code board:leaderboard}) im eigenen read-only Ranglisten-Channel,
 * per Edit aktualisiert über den gemeinsamen {@link TrackedBoardPublisher}
 * (Recovery/Cleanup wie F7). Trigger ist die Auto-Auswertung (F5): nach jedem
 * Auswertungs-<b>Batch</b> wird das aktuelle Ranking gegen den gespeicherten
 * vorherigen Rang gedifft (↑/↓/–/NEU), das Board editiert und die neue
 * Vergleichsbasis persistiert.
 */
@Service
public class LeaderboardBoardService {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardBoardService.class);

    /** Leaderboard-Board-Slot in {@code bot_messages}. */
    static final String BOARD_KEY = "board:leaderboard";

    private final JDA jda;
    private final String channelId;
    private final int topN;
    private final TipRepository tips;
    private final LeaderboardSnapshotRepository snapshots;
    private final LeaderboardBoardEmbed embed;
    private final TrackedBoardPublisher publisher;
    private final Clock clock;

    public LeaderboardBoardService(JDA jda,
                                   AppProperties properties,
                                   TipRepository tips,
                                   LeaderboardSnapshotRepository snapshots,
                                   LeaderboardBoardEmbed embed,
                                   TrackedBoardPublisher publisher,
                                   Clock clock) {
        this.jda = jda;
        this.channelId = properties.discord().leaderboardChannelId();
        this.topN = properties.leaderboard().topN();
        this.tips = tips;
        this.snapshots = snapshots;
        this.embed = embed;
        this.publisher = publisher;
        this.clock = clock;
    }

    /**
     * Start-Cleanup verwaister eigener Nachrichten und initialer Aufbau des Boards
     * (ohne die Vergleichsbasis neu zu schreiben – die Pfeile beziehen sich auf den
     * vor dem Neustart gespeicherten Snapshot, FR-007).
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() throws InterruptedException {
        TextChannel channel = resolveChannel();
        if (channel == null) {
            return;
        }
        jda.awaitReady();
        publisher.cleanupOrphans(channel, BOARD_KEY);
        renderBoard(channel, snapshots.findAllRanks());
    }

    /**
     * Nach einem Auswertungs-Batch (≥1 ausgewertetes Spiel): Ranking neu berechnen,
     * gegen den vorigen Snapshot diffen, Board editieren und neue Vergleichsbasis
     * (alle Teilnehmer) persistieren.
     */
    public void refreshAfterEvaluation() {
        TextChannel channel = resolveChannel();
        if (channel == null) {
            return;
        }
        Map<String, Integer> previous = snapshots.findAllRanks();
        List<RankedRow> rows = renderBoard(channel, previous);
        snapshots.replaceAll(LeaderboardRanking.ranksByUser(rows), clock.instant());
    }

    private List<RankedRow> renderBoard(TextChannel channel, Map<String, Integer> previous) {
        List<LeaderboardEntry> current = tips.leaderboard();
        List<RankedRow> rows = LeaderboardRanking.compute(current, previous);
        publisher.editOrPost(channel, BOARD_KEY, embed.build(rows, topN), List.of());
        log.info("Leaderboard-Board aktualisiert: {} Teilnehmer", rows.size());
        return rows;
    }

    private TextChannel resolveChannel() {
        if (channelId == null || channelId.isBlank()) {
            log.warn("Kein Ranglisten-Channel konfiguriert (DISCORD_LEADERBOARD_CHANNEL_ID) – Board inaktiv");
            return null;
        }
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            log.warn("Ranglisten-Channel {} nicht gefunden – ist der Bot im Server und hat dort Rechte?", channelId);
        }
        return channel;
    }
}
