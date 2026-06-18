package com.example.wmtippspiel.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.example.wmtippspiel.persistence.LeaderboardEntry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit-Tests der reinen Ranglisten-/Diff-Logik (F11): Standard-Competition-Ranking
 * und die Rang-Veränderung (↑/↓/–/NEU) gegen den vorigen Auswertungs-Batch.
 */
class LeaderboardRankDiffTest {

    private static LeaderboardEntry entry(String userId, int points, int exact) {
        return new LeaderboardEntry(userId, userId, points, exact, exact);
    }

    @Test
    @DisplayName("Symbol: NEU bei fehlender Vergleichsbasis")
    void symbolNew() {
        assertThat(new RankDelta(3, null).symbol()).isEqualTo("NEU");
    }

    @Test
    @DisplayName("Symbol: Aufstieg ↑n, Abstieg ↓n, unverändert –")
    void symbolUpDownSame() {
        assertThat(new RankDelta(3, 5).symbol()).isEqualTo("↑2"); // von 5 auf 3
        assertThat(new RankDelta(4, 3).symbol()).isEqualTo("↓1"); // von 3 auf 4
        assertThat(new RankDelta(2, 2).symbol()).isEqualTo("–");
    }

    @Test
    @DisplayName("Ranking: Standard-Competition-Rang teilt sich bei gleichen Punkten UND exakten Treffern")
    void standardCompetitionRanking() {
        List<LeaderboardEntry> sorted = List.of(
                entry("a", 10, 2),
                entry("b", 10, 2),   // gleich wie a → selber Rang 1
                entry("c", 7, 1),    // Rang 3 (überspringt 2)
                entry("d", 4, 0));   // Rang 4

        List<RankedRow> rows = LeaderboardRanking.compute(sorted, Map.of());

        assertThat(rows).extracting(RankedRow::rank).containsExactly(1, 1, 3, 4);
        assertThat(rows).allSatisfy(r -> assertThat(r.delta().symbol()).isEqualTo("NEU"));
    }

    @Test
    @DisplayName("Diff gegen vorigen Batch: Auf-/Abstieg korrekt, auch nach Neustart aus persistiertem Snapshot")
    void diffAgainstPreviousBatch() {
        List<LeaderboardEntry> sorted = List.of(
                entry("a", 12, 3),   // war Rang 2 → jetzt 1 ⇒ ↑1
                entry("b", 9, 1),    // war Rang 1 → jetzt 2 ⇒ ↓1
                entry("c", 5, 0));   // neu ⇒ NEU
        Map<String, Integer> previous = Map.of("a", 2, "b", 1);

        List<RankedRow> rows = LeaderboardRanking.compute(sorted, previous);

        assertThat(rows.get(0).entry().userId()).isEqualTo("a");
        assertThat(rows.get(0).delta().symbol()).isEqualTo("↑1");
        assertThat(rows.get(1).delta().symbol()).isEqualTo("↓1");
        assertThat(rows.get(2).delta().symbol()).isEqualTo("NEU");
    }

    @Test
    @DisplayName("ranksByUser liefert die neue Vergleichsbasis für ALLE Teilnehmer")
    void ranksByUserCoversEveryone() {
        List<RankedRow> rows = LeaderboardRanking.compute(
                List.of(entry("a", 10, 2), entry("b", 10, 2), entry("c", 3, 0)), Map.of());

        assertThat(LeaderboardRanking.ranksByUser(rows))
                .containsEntry("a", 1)
                .containsEntry("b", 1)
                .containsEntry("c", 3);
    }
}
