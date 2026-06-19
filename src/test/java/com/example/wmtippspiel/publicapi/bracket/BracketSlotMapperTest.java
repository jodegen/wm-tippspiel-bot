package com.example.wmtippspiel.publicapi.bracket;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.MatchStatus;
import com.example.wmtippspiel.domain.model.Stage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests der Slot→FIFA-Match-Nr-Zuordnung (Feature 010, FR-005/006). */
class BracketSlotMapperTest {

    private static Match ko(long id, Stage stage, Instant kickoff) {
        return new Match(id, "TBD", "TBD", kickoff, stage, null, null, null, null, null,
                null, null, MatchStatus.SCHEDULED, false, false);
    }

    @Test
    @DisplayName("LAST_32 nach kickoff sortiert → fortlaufende FIFA-Nrn 73..n")
    void last32SortedByKickoff() {
        Match a = ko(500L, Stage.LAST_32, Instant.parse("2026-06-29T16:00:00Z"));
        Match b = ko(501L, Stage.LAST_32, Instant.parse("2026-06-29T20:00:00Z"));
        Match c = ko(502L, Stage.LAST_32, Instant.parse("2026-06-30T16:00:00Z"));

        // bewusst unsortierte Eingabe
        Map<Integer, Match> map = BracketSlotMapper.assignSlots(List.of(c, a, b));

        assertThat(map.get(73)).isEqualTo(a);
        assertThat(map.get(74)).isEqualTo(b);
        assertThat(map.get(75)).isEqualTo(c);
    }

    @Test
    @DisplayName("Gleicher kickoff → Tie-Breaker id (aufsteigend) bestimmt die Slot-Reihenfolge")
    void tieBreakerById() {
        Instant same = Instant.parse("2026-06-29T16:00:00Z");
        Match higher = ko(900L, Stage.LAST_32, same);
        Match lower = ko(100L, Stage.LAST_32, same);

        Map<Integer, Match> map = BracketSlotMapper.assignSlots(List.of(higher, lower));

        assertThat(map.get(73)).isEqualTo(lower);
        assertThat(map.get(74)).isEqualTo(higher);
    }

    @Test
    @DisplayName("Jede Stage nutzt ihren eigenen Offset (Achtelfinale → 89, Finale → 104)")
    void offsetsPerStage() {
        Match l16 = ko(1L, Stage.LAST_16, Instant.parse("2026-07-04T16:00:00Z"));
        Match qf = ko(2L, Stage.QUARTER_FINAL, Instant.parse("2026-07-10T16:00:00Z"));
        Match sf = ko(3L, Stage.SEMI_FINAL, Instant.parse("2026-07-14T16:00:00Z"));
        Match third = ko(4L, Stage.THIRD_PLACE, Instant.parse("2026-07-18T16:00:00Z"));
        Match fin = ko(5L, Stage.FINAL, Instant.parse("2026-07-19T16:00:00Z"));

        Map<Integer, Match> map = BracketSlotMapper.assignSlots(List.of(l16, qf, sf, third, fin));

        assertThat(map.get(89)).isEqualTo(l16);
        assertThat(map.get(97)).isEqualTo(qf);
        assertThat(map.get(101)).isEqualTo(sf);
        assertThat(map.get(103)).isEqualTo(third);
        assertThat(map.get(104)).isEqualTo(fin);
    }

    @Test
    @DisplayName("Unvollständige Stage: vorhandene Spiele füllen die ersten Slots, Rest bleibt unbelegt")
    void incompleteStage() {
        Match only = ko(1L, Stage.LAST_16, Instant.parse("2026-07-04T16:00:00Z"));

        Map<Integer, Match> map = BracketSlotMapper.assignSlots(List.of(only));

        assertThat(map.get(89)).isEqualTo(only);
        assertThat(map).doesNotContainKey(90); // strukturelle Vollständigkeit kommt aus der Topologie, nicht aus den Daten
    }

    @Test
    @DisplayName("Gruppenspiele werden ignoriert")
    void ignoresGroupStage() {
        Match group = ko(1L, Stage.GROUP_STAGE, Instant.parse("2026-06-12T16:00:00Z"));

        Map<Integer, Match> map = BracketSlotMapper.assignSlots(List.of(group));

        assertThat(map).isEmpty();
    }
}
