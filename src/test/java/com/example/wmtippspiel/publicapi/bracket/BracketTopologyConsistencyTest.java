package com.example.wmtippspiel.publicapi.bracket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.example.wmtippspiel.domain.model.Stage;
import com.example.wmtippspiel.publicapi.bracket.BracketTopology.SourceRole;
import com.example.wmtippspiel.publicapi.bracket.BracketTopology.TopologyEntry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pflicht-Test (Feature 010): sichert die statische FIFA-Topologie auf
 * Konsistenz ab (SC-002). Schlägt fehl, sobald eine Kante der hinterlegten
 * Halbbaum-Logik widerspricht.
 */
class BracketTopologyConsistencyTest {

    private static final List<TopologyEntry> ENTRIES = BracketTopology.ENTRIES;

    private static Map<Integer, TopologyEntry> byNo() {
        Map<Integer, TopologyEntry> map = new HashMap<>();
        for (TopologyEntry e : ENTRIES) {
            map.put(e.fifaMatchNo(), e);
        }
        return map;
    }

    @Test
    @DisplayName("Genau 32 Knoten mit lückenlosen FIFA-Nrn 73–104")
    void thirtyTwoNodesContiguous() {
        assertThat(ENTRIES).hasSize(32);
        Set<Integer> nos = ENTRIES.stream().map(TopologyEntry::fifaMatchNo).collect(Collectors.toSet());
        assertThat(nos).hasSize(32);
        for (int n = 73; n <= 104; n++) {
            assertThat(nos).contains(n);
        }
    }

    @Test
    @DisplayName("Spielanzahl je Runde: 16/8/4/2/1/1")
    void countsPerStage() {
        Map<Stage, Long> counts = ENTRIES.stream()
                .collect(Collectors.groupingBy(TopologyEntry::stage, Collectors.counting()));
        assertThat(counts.get(Stage.LAST_32)).isEqualTo(16);
        assertThat(counts.get(Stage.LAST_16)).isEqualTo(8);
        assertThat(counts.get(Stage.QUARTER_FINAL)).isEqualTo(4);
        assertThat(counts.get(Stage.SEMI_FINAL)).isEqualTo(2);
        assertThat(counts.get(Stage.THIRD_PLACE)).isEqualTo(1);
        assertThat(counts.get(Stage.FINAL)).isEqualTo(1);
    }

    @Test
    @DisplayName("LAST_32 hat keine Quellen; jedes andere Spiel genau 2 eindeutige, existierende Quellen")
    void sourcesWellFormed() {
        Set<Integer> all = ENTRIES.stream().map(TopologyEntry::fifaMatchNo).collect(Collectors.toSet());
        for (TopologyEntry e : ENTRIES) {
            if (e.stage() == Stage.LAST_32) {
                assertThat(e.sourceMatchNos()).isEmpty();
            } else {
                assertThat(e.sourceMatchNos()).hasSize(2);
                assertThat(Set.copyOf(e.sourceMatchNos())).hasSize(2); // eindeutig
                assertThat(all).containsAll(e.sourceMatchNos()); // existierend
            }
        }
    }

    @Test
    @DisplayName("Sieger-Kanten konsistent invertierbar: WINNER-Quelle X von Z ⇒ nextMatchNo(X)=Z")
    void winnerEdgesInvertible() {
        Map<Integer, TopologyEntry> byNo = byNo();
        for (TopologyEntry z : ENTRIES) {
            if (z.sourceRole() != SourceRole.WINNER) {
                continue;
            }
            for (int sourceNo : z.sourceMatchNos()) {
                assertThat(byNo.get(sourceNo).nextMatchNo())
                        .as("nextMatchNo(%d) muss %d sein", sourceNo, z.fifaMatchNo())
                        .isEqualTo(z.fifaMatchNo());
            }
        }
    }

    @Test
    @DisplayName("Finale (104) = Sieger der beiden Halbfinals (101, 102)")
    void finalFromSemifinals() {
        TopologyEntry fin = byNo().get(104);
        assertThat(fin.stage()).isEqualTo(Stage.FINAL);
        assertThat(fin.sourceRole()).isEqualTo(SourceRole.WINNER);
        assertThat(fin.sourceMatchNos()).containsExactlyInAnyOrder(101, 102);
        assertThat(fin.nextMatchNo()).isNull();
    }

    @Test
    @DisplayName("Spiel um Platz 3 (103) = Verlierer der beiden Halbfinals (101, 102)")
    void thirdPlaceFromSemifinalLosers() {
        TopologyEntry third = byNo().get(103);
        assertThat(third.stage()).isEqualTo(Stage.THIRD_PLACE);
        assertThat(third.sourceRole()).isEqualTo(SourceRole.LOSER);
        assertThat(third.sourceMatchNos()).containsExactlyInAnyOrder(101, 102);
        assertThat(third.nextMatchNo()).isNull();
    }

    @Test
    @DisplayName("Nur 103/104 sind Endknoten ohne nextMatchNo; keine Zyklen (next ist stets größer)")
    void terminalsAndNoCycles() {
        Map<Integer, TopologyEntry> byNo = byNo();
        for (TopologyEntry e : ENTRIES) {
            if (e.fifaMatchNo() == 103 || e.fifaMatchNo() == 104) {
                assertThat(e.nextMatchNo()).isNull();
            } else {
                assertThat(e.nextMatchNo()).isNotNull();
                assertThat(byNo).containsKey(e.nextMatchNo());
                assertThat(e.nextMatchNo()).isGreaterThan(e.fifaMatchNo()); // azyklisch
            }
        }
    }

    @Test
    @DisplayName("F1: internalStageToApiStage liefert genau das football-data-Vokabular der 6 Runden")
    void apiStageVocabulary() {
        assertThat(BracketTopology.apiStage(Stage.LAST_32)).isEqualTo("LAST_32");
        assertThat(BracketTopology.apiStage(Stage.LAST_16)).isEqualTo("LAST_16");
        assertThat(BracketTopology.apiStage(Stage.QUARTER_FINAL)).isEqualTo("QUARTER_FINALS");
        assertThat(BracketTopology.apiStage(Stage.SEMI_FINAL)).isEqualTo("SEMI_FINALS");
        assertThat(BracketTopology.apiStage(Stage.THIRD_PLACE)).isEqualTo("THIRD_PLACE");
        assertThat(BracketTopology.apiStage(Stage.FINAL)).isEqualTo("FINAL");
    }

    @Test
    @DisplayName("FIFA-Match-Nr aus Stage+Slot deckt 73–104 ab")
    void fifaMatchNoFormula() {
        assertThat(BracketTopology.fifaMatchNo(Stage.LAST_32, 1)).isEqualTo(73);
        assertThat(BracketTopology.fifaMatchNo(Stage.LAST_32, 16)).isEqualTo(88);
        assertThat(BracketTopology.fifaMatchNo(Stage.LAST_16, 1)).isEqualTo(89);
        assertThat(BracketTopology.fifaMatchNo(Stage.QUARTER_FINAL, 1)).isEqualTo(97);
        assertThat(BracketTopology.fifaMatchNo(Stage.SEMI_FINAL, 2)).isEqualTo(102);
        assertThat(BracketTopology.fifaMatchNo(Stage.THIRD_PLACE, 1)).isEqualTo(103);
        assertThat(BracketTopology.fifaMatchNo(Stage.FINAL, 1)).isEqualTo(104);
    }
}
