package com.example.wmtippspiel.discord.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.MatchStatus;
import com.example.wmtippspiel.domain.model.Stage;

import net.dv8tion.jda.api.entities.MessageEmbed;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit-Tests für das konsolidierte Board-Embed (F7-Redesign): Listenformat,
 * optionale Felder, Leerzustand und defensive Truncation unter den
 * Discord-Limits. (Empfohlen, nicht Prinzip-III-pflichtig.)
 */
class BoardEmbedTest {

    private static final Instant NOW = Instant.parse("2026-06-13T12:00:00Z");

    private final BoardEmbed boardEmbed = new BoardEmbed(
            new TimeFormatting(ZoneId.of("Europe/Berlin")),
            new EmbedStyle(Clock.fixed(NOW, ZoneOffset.UTC)));

    private Match match(long id, String home, String away, String channel,
                        BigDecimal oddsH, BigDecimal oddsD, BigDecimal oddsA) {
        return new Match(id, home, away, NOW.plusSeconds(3600 * id), Stage.GROUP_STAGE, "A",
                channel, oddsH, oddsD, oddsA, null, null, MatchStatus.SCHEDULED, false, false);
    }

    @Test
    @DisplayName("Listet Begegnung, Countdown, Sender und Quote in der Beschreibung")
    void rendersFullLine() {
        Match m = match(1, "Deutschland", "Brasilien", "ARD",
                new BigDecimal("1.80"), new BigDecimal("3.40"), new BigDecimal("4.20"));

        MessageEmbed embed = boardEmbed.buildBoard(List.of(m));
        String desc = embed.getDescription();

        assertThat(desc).contains("**Deutschland vs Brasilien**");
        assertThat(desc).contains("<t:" + m.kickoff().getEpochSecond() + ":R>");
        assertThat(desc).contains("📺 ARD");
        assertThat(desc).contains("💰 1.8/3.4/4.2");
    }

    @Test
    @DisplayName("Lässt fehlende optionale Felder (Sender/Quote) ohne Platzhalter aus")
    void omitsMissingOptionals() {
        Match m = match(1, "Japan", "Mexiko", null, null, null, null);

        String desc = boardEmbed.buildBoard(List.of(m)).getDescription();

        assertThat(desc).contains("**Japan vs Mexiko**");
        assertThat(desc).doesNotContain("📺");
        assertThat(desc).doesNotContain("💰");
        assertThat(desc).doesNotContain("null");
    }

    @Test
    @DisplayName("Zeigt freundlichen Leer-Hinweis statt leerer Liste")
    void emptyState() {
        String desc = boardEmbed.buildBoard(List.of()).getDescription();

        assertThat(desc).isNotBlank();
        assertThat(desc).contains("keine anstehenden Spiele");
    }

    @Test
    @DisplayName("Tronkiert defensiv unter den Discord-Limits und hängt einen Rest-Hinweis an")
    void truncatesUnderLimits() {
        // Sehr lange Namen erzwingen einen Überlauf der Beschreibung.
        String longName = "X".repeat(400);
        List<Match> many = new ArrayList<>();
        for (int i = 1; i <= 40; i++) {
            many.add(match(i, longName + i, longName, "Sender", null, null, null));
        }

        MessageEmbed embed = boardEmbed.buildBoard(many);
        String desc = embed.getDescription();

        assertThat(desc.length()).isLessThanOrEqualTo(4096);
        assertThat(embed.getLength()).isLessThan(6000);
        assertThat(desc).contains("… und");
        assertThat(desc).contains("weitere");
    }
}
