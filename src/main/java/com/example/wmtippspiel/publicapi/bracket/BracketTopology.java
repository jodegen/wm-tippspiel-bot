package com.example.wmtippspiel.publicapi.bracket;

import java.util.List;
import java.util.Map;

import com.example.wmtippspiel.domain.model.Stage;

/**
 * Statische, unveränderliche FIFA-Bracket-Topologie der WM 2026 (Feature 010).
 * Bildet {@code WC2026_BRACKET_TOPOLOGY.md} 1:1 im Code ab — die feste K.o.-
 * Struktur (FIFA-Match 73–104 inkl. aller Quell→Ziel-Kanten) und die LAST_32-
 * Platzhalter-Labels. Wird NICHT in der DB gehalten (FR-004). Die Konsistenz
 * (32 Knoten, je 2 eindeutige Quellen, FINAL = Sieger beider Halbfinals usw.)
 * sichert {@code BracketTopologyConsistencyTest} ab.
 *
 * <p>{@code nextMatchNo} bezeichnet stets das Ziel des <b>Siegers</b>; der
 * Verlierer-Pfad existiert nur für das Spiel um Platz 3 (Match 103) und ergibt
 * sich aus dessen {@link #sourceMatchNos()} mit {@link SourceRole#LOSER}.
 */
public final class BracketTopology {

    private BracketTopology() {
    }

    /** Rolle, mit der ein Spiel seine Quell-Spiele konsumiert. */
    public enum SourceRole {
        WINNER,
        LOSER
    }

    /**
     * Ein fester Knoten des Baums.
     *
     * @param stage          interne Turnierphase
     * @param slotIndex      1..n innerhalb der Stage (kickoff-sortiert)
     * @param fifaMatchNo    73–104, stabil
     * @param sourceMatchNos 0 (LAST_32) oder genau 2 FIFA-Nrn
     * @param nextMatchNo    Ziel-Match des Siegers; {@code null} für 103/104
     * @param sourceRole     wie die Quellen konsumiert werden (LOSER nur für 103)
     */
    public record TopologyEntry(Stage stage, int slotIndex, int fifaMatchNo,
                                List<Integer> sourceMatchNos, Integer nextMatchNo, SourceRole sourceRole) {
    }

    /** Heim-/Auswärts-Platzhalterlabel eines LAST_32-Spiels (FR-008). */
    public record Last32Placeholder(String home, String away) {
    }

    /** Anzeigereihenfolge der sechs Runden (FR-002). */
    public static final List<Stage> ROUND_ORDER = List.of(
            Stage.LAST_32, Stage.LAST_16, Stage.QUARTER_FINAL, Stage.SEMI_FINAL, Stage.THIRD_PLACE, Stage.FINAL);

    private static TopologyEntry last32(int slot, int fifaNo, int nextNo) {
        return new TopologyEntry(Stage.LAST_32, slot, fifaNo, List.of(), nextNo, SourceRole.WINNER);
    }

    private static TopologyEntry winnerNode(Stage stage, int slot, int fifaNo, int srcA, int srcB, Integer nextNo) {
        return new TopologyEntry(stage, slot, fifaNo, List.of(srcA, srcB), nextNo, SourceRole.WINNER);
    }

    /** Alle 32 Knoten (FIFA 73–104) in fester Reihenfolge. */
    public static final List<TopologyEntry> ENTRIES = List.of(
            // LAST_32 (73–88): nextMatchNo = das LAST_16-Spiel, das diesen Sieger aufnimmt.
            last32(1, 73, 90), last32(2, 74, 89), last32(3, 75, 90), last32(4, 76, 91),
            last32(5, 77, 89), last32(6, 78, 91), last32(7, 79, 92), last32(8, 80, 92),
            last32(9, 81, 94), last32(10, 82, 94), last32(11, 83, 93), last32(12, 84, 93),
            last32(13, 85, 96), last32(14, 86, 95), last32(15, 87, 96), last32(16, 88, 95),
            // LAST_16 (89–96)
            winnerNode(Stage.LAST_16, 1, 89, 74, 77, 97),
            winnerNode(Stage.LAST_16, 2, 90, 73, 75, 97),
            winnerNode(Stage.LAST_16, 3, 91, 76, 78, 99),
            winnerNode(Stage.LAST_16, 4, 92, 79, 80, 99),
            winnerNode(Stage.LAST_16, 5, 93, 83, 84, 98),
            winnerNode(Stage.LAST_16, 6, 94, 81, 82, 98),
            winnerNode(Stage.LAST_16, 7, 95, 86, 88, 100),
            winnerNode(Stage.LAST_16, 8, 96, 85, 87, 100),
            // QUARTER_FINALS (97–100)
            winnerNode(Stage.QUARTER_FINAL, 1, 97, 89, 90, 101),
            winnerNode(Stage.QUARTER_FINAL, 2, 98, 93, 94, 101),
            winnerNode(Stage.QUARTER_FINAL, 3, 99, 91, 92, 102),
            winnerNode(Stage.QUARTER_FINAL, 4, 100, 95, 96, 102),
            // SEMI_FINALS (101–102): Sieger → Finale (104); Verlierer → Spiel um Platz 3 (103, s. u.).
            winnerNode(Stage.SEMI_FINAL, 1, 101, 97, 98, 104),
            winnerNode(Stage.SEMI_FINAL, 2, 102, 99, 100, 104),
            // THIRD_PLACE (103): Verlierer beider Halbfinals.
            new TopologyEntry(Stage.THIRD_PLACE, 1, 103, List.of(101, 102), null, SourceRole.LOSER),
            // FINAL (104): Sieger beider Halbfinals.
            new TopologyEntry(Stage.FINAL, 1, 104, List.of(101, 102), null, SourceRole.WINNER));

    /** LAST_32-Platzhalter je FIFA-Match-Nr (73–88), exakt nach WC2026_BRACKET_TOPOLOGY.md. */
    public static final Map<Integer, Last32Placeholder> LAST32_PLACEHOLDERS = Map.ofEntries(
            Map.entry(73, new Last32Placeholder("Sieger Gruppe A", "Zweiter Gruppe B")),
            Map.entry(74, new Last32Placeholder("Sieger Gruppe E", "Dritter A/B/C/D/F")),
            Map.entry(75, new Last32Placeholder("Sieger Gruppe F", "Zweiter Gruppe C")),
            Map.entry(76, new Last32Placeholder("Sieger Gruppe C", "Zweiter Gruppe F")),
            Map.entry(77, new Last32Placeholder("Sieger Gruppe I", "Dritter C/D/F/G/H")),
            Map.entry(78, new Last32Placeholder("Zweiter Gruppe E", "Zweiter Gruppe I")),
            Map.entry(79, new Last32Placeholder("Sieger Gruppe A", "Dritter C/E/F/H/I")),
            Map.entry(80, new Last32Placeholder("Sieger Gruppe L", "Dritter E/H/I/J/K")),
            Map.entry(81, new Last32Placeholder("Sieger Gruppe D", "Dritter B/E/F/I/J")),
            Map.entry(82, new Last32Placeholder("Sieger Gruppe G", "Dritter A/E/H/I/J")),
            Map.entry(83, new Last32Placeholder("Zweiter Gruppe K", "Zweiter Gruppe L")),
            Map.entry(84, new Last32Placeholder("Sieger Gruppe H", "Zweiter Gruppe J")),
            Map.entry(85, new Last32Placeholder("Sieger Gruppe B", "Dritter E/F/G/I/J")),
            Map.entry(86, new Last32Placeholder("Sieger Gruppe J", "Zweiter Gruppe H")),
            Map.entry(87, new Last32Placeholder("Sieger Gruppe K", "Dritter D/E/I/J/L")),
            Map.entry(88, new Last32Placeholder("Zweiter Gruppe D", "Zweiter Gruppe G")));

    /** Basis-Offset je Stage: FIFA-Match-Nr = Offset + Slot-Index. */
    public static int fifaMatchNo(Stage stage, int slotIndex) {
        int offset = switch (stage) {
            case LAST_32 -> 72;
            case LAST_16 -> 88;
            case QUARTER_FINAL -> 96;
            case SEMI_FINAL -> 100;
            case THIRD_PLACE -> 102;
            case FINAL -> 103;
            case GROUP_STAGE -> throw new IllegalArgumentException("GROUP_STAGE ist kein K.o.-Spiel");
        };
        return offset + slotIndex;
    }

    /** Stabiles football-data-Vokabular (Singular → Plural) für die DTO-Ausgabe (F1/R7). */
    public static String apiStage(Stage stage) {
        return switch (stage) {
            case LAST_32 -> "LAST_32";
            case LAST_16 -> "LAST_16";
            case QUARTER_FINAL -> "QUARTER_FINALS";
            case SEMI_FINAL -> "SEMI_FINALS";
            case THIRD_PLACE -> "THIRD_PLACE";
            case FINAL -> "FINAL";
            case GROUP_STAGE -> "GROUP_STAGE";
        };
    }

    /** Deutscher Anzeigename der Runde. */
    public static String roundLabel(Stage stage) {
        return switch (stage) {
            case LAST_32 -> "Sechzehntelfinale";
            case LAST_16 -> "Achtelfinale";
            case QUARTER_FINAL -> "Viertelfinale";
            case SEMI_FINAL -> "Halbfinale";
            case THIRD_PLACE -> "Spiel um Platz 3";
            case FINAL -> "Finale";
            case GROUP_STAGE -> "Gruppenphase";
        };
    }
}
