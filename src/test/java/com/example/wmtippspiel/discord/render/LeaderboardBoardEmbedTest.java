package com.example.wmtippspiel.discord.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.wmtippspiel.config.AppProperties;
import com.example.wmtippspiel.leaderboard.LeaderboardRanking;
import com.example.wmtippspiel.leaderboard.RankedRow;
import com.example.wmtippspiel.persistence.LeaderboardEntry;
import com.example.wmtippspiel.publicapi.PublicIdService;

import net.dv8tion.jda.api.entities.MessageEmbed;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Härtung der Embed-Limits für das Leaderboard-Board (F11, FR-008/SC-004): die
 * Beschreibung bleibt auch bei vielen/langen Namen unter dem Discord-Hardlimit.
 * Zusätzlich (Feature 009): der konfigurierbare Footer-Hinweis auf die Web-Tabelle.
 */
class LeaderboardBoardEmbedTest {

    private final LeaderboardBoardEmbed embed = embedWith(null);

    private static LeaderboardBoardEmbed embedWith(String websiteBaseUrl) {
        EmbedStyle style = new EmbedStyle(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        AppProperties props = new AppProperties(null, null, null, null, null,
                new AppProperties.PublicApi(List.of(), "test-secret", 5),
                websiteBaseUrl == null ? null : new AppProperties.Website(websiteBaseUrl));
        WebsiteLinks links = new WebsiteLinks(props, new PublicIdService(props));
        return new LeaderboardBoardEmbed(style, links);
    }

    @Test
    @DisplayName("Top-N begrenzt die Zeilenzahl")
    void limitsToTopN() {
        List<RankedRow> rows = rows(50);
        MessageEmbed result = embed.build(rows, 15);
        long lines = result.getDescription().lines().count();
        assertThat(lines).isEqualTo(15);
    }

    @Test
    @DisplayName("Sehr lange Namen werden defensiv abgeschnitten (< 4096 Zeichen)")
    void truncatesDefensivelyUnderHardLimit() {
        List<LeaderboardEntry> entries = new ArrayList<>();
        String longName = "X".repeat(200);
        for (int i = 0; i < 500; i++) {
            entries.add(new LeaderboardEntry("u" + i, longName + i, 500 - i, 0, 0));
        }
        List<RankedRow> rows = LeaderboardRanking.compute(entries, Map.of());

        MessageEmbed result = embed.build(rows, 1000); // Top-N absichtlich groß

        assertThat(result.getDescription().length())
                .isLessThanOrEqualTo(MessageEmbed.DESCRIPTION_MAX_LENGTH);
    }

    @Test
    @DisplayName("Leeres Board zeigt einen freundlichen Hinweis")
    void emptyBoard() {
        assertThat(embed.build(List.of(), 15).getDescription()).contains("Noch keine Tipps");
    }

    @Test
    @DisplayName("Footer enthält den Web-Hinweis bei konfigurierter Basis-URL (auch leeres Board, FR-001/SC-001)")
    void footerHintWhenConfigured() {
        LeaderboardBoardEmbed configured = embedWith("https://wm.xenoria.de");

        String populatedFooter = configured.build(rows(3), 15).getFooter().getText();
        String emptyFooter = configured.build(List.of(), 15).getFooter().getText();

        assertThat(populatedFooter).contains("Vollständige Tabelle auf wm.xenoria.de");
        assertThat(emptyFooter).contains("Vollständige Tabelle auf wm.xenoria.de");
    }

    @Test
    @DisplayName("Ohne Basis-URL bleibt der Footer unverändert (FR-006)")
    void footerUnchangedWhenNotConfigured() {
        String footer = embed.build(rows(3), 15).getFooter().getText();
        assertThat(footer).isEqualTo(EmbedStyle.FOOTER_BASE);
    }

    private static List<RankedRow> rows(int n) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            entries.add(new LeaderboardEntry("u" + i, "User" + i, n - i, 0, 0));
        }
        return LeaderboardRanking.compute(entries, Map.of());
    }
}
