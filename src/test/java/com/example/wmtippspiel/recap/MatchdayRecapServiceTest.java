package com.example.wmtippspiel.recap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.example.wmtippspiel.discord.publish.AnnounceChannel;
import com.example.wmtippspiel.discord.render.MatchdayRecapEmbed;
import com.example.wmtippspiel.persistence.MatchRepository;
import com.example.wmtippspiel.persistence.MatchdayRecapRepository;
import com.example.wmtippspiel.persistence.MatchdayScore;
import com.example.wmtippspiel.persistence.MatchdayTipRow;
import com.example.wmtippspiel.persistence.TipRepository;

import net.dv8tion.jda.api.entities.MessageEmbed;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit-Tests des Spieltags-Rückblicks (F12): Vollständigkeits-/Posting-Trigger,
 * Idempotenz über {@code tryClaim}, Leerfall, Startup-Seeding und die reine
 * Best-Tipp-Auswahl (Quoten-Tie-Break, deterministisch).
 */
class MatchdayRecapServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-20T22:00:00Z");

    private MatchRepository matches;
    private TipRepository tips;
    private MatchdayRecapRepository recaps;
    private MatchdayRecapEmbed embed;
    private AnnounceChannel announce;
    private MatchdayRecapService service;

    @BeforeEach
    void setUp() {
        matches = mock(MatchRepository.class);
        tips = mock(TipRepository.class);
        recaps = mock(MatchdayRecapRepository.class);
        embed = mock(MatchdayRecapEmbed.class);
        announce = mock(AnnounceChannel.class);
        when(embed.build(any())).thenReturn(mock(MessageEmbed.class));
        when(tips.matchdayLeaderboard(any())).thenReturn(List.of());
        when(tips.matchdayEvaluatedTips(any())).thenReturn(List.of());
        service = new MatchdayRecapService(matches, tips, recaps, embed, announce,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Abgeschlossener, noch nicht geposteter Spieltag → genau ein Post")
    void completedMatchdayPostsOnce() {
        when(matches.findCompletedRecapKeys()).thenReturn(List.of("md:1"));
        when(recaps.tryClaim(eq("md:1"), any())).thenReturn(true);
        when(tips.matchdayLeaderboard("md:1")).thenReturn(List.of(
                new MatchdayScore("u1", "Alice", 7),
                new MatchdayScore("u2", "Bob", 0)));

        service.postCompletedRecaps();

        verify(announce).postPlain(any());
    }

    @Test
    @DisplayName("Bereits geposteter Spieltag (tryClaim=false) → kein zweiter Post (idempotent, auch nach Re-Eval/Neustart)")
    void alreadyPostedDoesNotRepost() {
        when(matches.findCompletedRecapKeys()).thenReturn(List.of("md:1"));
        when(recaps.tryClaim(eq("md:1"), any())).thenReturn(false);

        service.postCompletedRecaps();

        verify(announce, never()).postPlain(any());
    }

    @Test
    @DisplayName("Unvollständiger Spieltag taucht nicht in findCompletedRecapKeys auf → kein Post")
    void incompleteMatchdayNotPosted() {
        when(matches.findCompletedRecapKeys()).thenReturn(List.of());

        service.postCompletedRecaps();

        verify(recaps, never()).tryClaim(any(), any());
        verify(announce, never()).postPlain(any());
    }

    @Test
    @DisplayName("Spieltag ohne Tipps wird sauber gepostet (kein Fehler)")
    void emptyMatchdayPostsCleanly() {
        when(matches.findCompletedRecapKeys()).thenReturn(List.of("md:2"));
        when(recaps.tryClaim(eq("md:2"), any())).thenReturn(true);
        // matchdayLeaderboard/evaluatedTips bereits leer (Default-Stub)

        service.postCompletedRecaps();

        verify(announce).postPlain(any());
    }

    @Test
    @DisplayName("Startup-Seeding markiert abgeschlossene Spieltage als erledigt, OHNE zu posten")
    void seedingClaimsWithoutPosting() {
        when(matches.findCompletedRecapKeys()).thenReturn(List.of("md:1", "stage:LAST_16"));
        when(recaps.tryClaim(any(), any())).thenReturn(true);

        service.seedExistingOnStartup();

        verify(recaps).tryClaim(eq("md:1"), any());
        verify(recaps).tryClaim(eq("stage:LAST_16"), any());
        verify(announce, never()).postPlain(any());
    }

    // --- reine Best-Tipp-Logik (MatchdayRecaps) ---

    @Test
    @DisplayName("Bester Tipp: ohne exakten Treffer gewinnt die höchste Punktzahl")
    void bestTipFallsBackToHighestPoints() {
        List<MatchdayTipRow> rows = List.of(
                tip("Alice", 3, odds("2.0", "3.0", "4.0"), 1, 0),  // 3 Pkt
                tip("Bob", 2, odds("2.0", "3.0", "4.0"), 1, 0));    // 2 Pkt
        assertThat(MatchdayRecaps.bestTip(rows)).get()
                .extracting(MatchdayTipRow::username).isEqualTo("Alice");
    }

    @Test
    @DisplayName("Tie-Break: bei Punktgleichheit gewinnt das unwahrscheinlichste Ergebnis (höchste Ergebnis-Quote)")
    void bestTipTieBreakByUnlikeliestResult() {
        MatchdayTipRow homeWin = tip("Alice", 4, odds("1.5", "3.0", "6.0"), 2, 0); // Heimsieg, Quote 1.5
        MatchdayTipRow awayWin = tip("Bob", 4, odds("1.5", "3.0", "6.0"), 0, 1);   // Auswärtssieg, Quote 6.0
        assertThat(MatchdayRecaps.bestTip(List.of(homeWin, awayWin))).get()
                .extracting(MatchdayTipRow::username).isEqualTo("Bob");
    }

    @Test
    @DisplayName("Fehlende Quoten ⇒ deterministischer Tie-Break (kein Zufall)")
    void bestTipDeterministicWhenOddsMissing() {
        MatchdayTipRow a = tip("Alice", 3, new BigDecimal[]{null, null, null}, 1, 0);
        MatchdayTipRow b = tip("Bob", 3, new BigDecimal[]{null, null, null}, 1, 0);
        Optional<MatchdayTipRow> first = MatchdayRecaps.bestTip(List.of(a, b));
        Optional<MatchdayTipRow> second = MatchdayRecaps.bestTip(List.of(b, a));
        assertThat(first).isPresent();
        assertThat(first.get().username()).isEqualTo(second.get().username()); // stabil, reihenfolgeunabhängig
    }

    @Test
    @DisplayName("Nur 0-Punkte-Tipps ⇒ kein bester Tipp")
    void noBestTipWhenAllZero() {
        assertThat(MatchdayRecaps.bestTip(List.of(tip("Alice", 0, odds("2", "3", "4"), 1, 0)))).isEmpty();
    }

    private static BigDecimal[] odds(String h, String d, String a) {
        return new BigDecimal[]{new BigDecimal(h), new BigDecimal(d), new BigDecimal(a)};
    }

    private static MatchdayTipRow tip(String user, int points, BigDecimal[] odds, int resultHome, int resultAway) {
        return new MatchdayTipRow(user, resultHome, resultAway, points, resultHome, resultAway,
                "Home", "Away", odds[0], odds[1], odds[2]);
    }
}
