package com.example.wmtippspiel.discord.commands;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.example.wmtippspiel.leaderboard.RankDelta;
import com.example.wmtippspiel.leaderboard.RankedRow;
import com.example.wmtippspiel.persistence.LeaderboardEntry;
import com.example.wmtippspiel.persistence.ProfileTipRow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit-Tests der reinen Profil-Aggregation (F13): Punktstufen-Verteilung,
 * Trefferquote (inkl. Division-durch-Null-Schutz), bester/schlechtester Tipp und
 * der Leerfall.
 */
class ProfilAggregationTest {

    private static RankedRow ranked(String userId, int rank, int totalPoints, int exactHits, int tipCount) {
        return new RankedRow(
                new LeaderboardEntry(userId, "Alice", totalPoints, tipCount, exactHits),
                rank, new RankDelta(rank, null));
    }

    private static ProfileTipRow tip(int points) {
        return new ProfileTipRow("Home", "Away", 1, 0, 1, 0, points,
                100L, java.time.Instant.parse("2026-06-20T19:00:00Z"), "GROUP_STAGE");
    }

    @Test
    @DisplayName("Vollständige Bilanz: Rang/Punkte, Verteilung, Trefferquote, bester/schlechtester Tipp")
    void fullProfile() {
        RankedRow mine = ranked("u1", 2, 13, 2, 5);
        List<ProfileTipRow> tips = List.of(
                new ProfileTipRow("A", "B", 2, 1, 2, 1, 4,
                        101L, java.time.Instant.parse("2026-06-21T19:00:00Z"), "GROUP_STAGE"),  // exakt
                new ProfileTipRow("C", "D", 1, 0, 1, 0, 4,
                        102L, java.time.Instant.parse("2026-06-22T19:00:00Z"), "GROUP_STAGE"),  // exakt
                tip(3), tip(2), tip(0));

        UserProfile p = ProfileStats.build("Alice", mine, tips);

        assertThat(p.rank()).isEqualTo(2);
        assertThat(p.totalPoints()).isEqualTo(13);
        assertThat(p.exactHits()).isEqualTo(2);
        assertThat(p.evaluatedTips()).isEqualTo(5);
        assertThat(p.count4()).isEqualTo(2);
        assertThat(p.count3()).isEqualTo(1);
        assertThat(p.count2()).isEqualTo(1);
        assertThat(p.count0()).isEqualTo(1);
        assertThat(p.hitRatePercent()).isEqualTo(40); // 2/5
        assertThat(p.best().points()).isEqualTo(4);
        assertThat(p.worst().points()).isZero();
        assertThat(p.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("Keine ausgewerteten Tipps: Trefferquote ist null (kein Division-durch-Null), kein bester/schlechtester Tipp")
    void noEvaluatedTips() {
        // In der Wertung (z. B. nur unausgewertete Tipps), aber keine ausgewerteten Zeilen.
        UserProfile p = ProfileStats.build("Alice", ranked("u1", 1, 0, 0, 3), List.of());

        assertThat(p.evaluatedTips()).isZero();
        assertThat(p.hitRatePercent()).isNull();
        assertThat(p.best()).isNull();
        assertThat(p.worst()).isNull();
    }

    @Test
    @DisplayName("Unbekannter Nutzer ohne Tipps: leere, gültige Bilanz statt Fehler")
    void emptyUser() {
        UserProfile p = ProfileStats.build("Niemand", null, List.of());

        assertThat(p.rank()).isNull();
        assertThat(p.totalPoints()).isZero();
        assertThat(p.hitRatePercent()).isNull();
        assertThat(p.isEmpty()).isTrue();
    }
}
