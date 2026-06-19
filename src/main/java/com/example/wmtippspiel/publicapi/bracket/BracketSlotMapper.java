package com.example.wmtippspiel.publicapi.bracket;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.Stage;

/**
 * Bildet vorliegende K.o.-Spiele auf ihre feste FIFA-Match-Nummer ab (Feature
 * 010, FR-005/006): je Stage nach {@code kickoff} aufsteigend (Tie-Breaker
 * {@code id} aufsteigend) sortieren, daraus Slot-Index 1..n, daraus über
 * {@link BracketTopology#fifaMatchNo(Stage, int)} die FIFA-Nr. Rein funktional,
 * ohne DB-Zugriff (testbar).
 */
public final class BracketSlotMapper {

    private static final Comparator<Match> BY_KICKOFF_THEN_ID =
            Comparator.comparing(Match::kickoff).thenComparingLong(Match::id);

    private BracketSlotMapper() {
    }

    /**
     * @param knockoutMatches alle K.o.-Spiele (Stage != GROUP_STAGE)
     * @return FIFA-Match-Nr → zugehöriges {@link Match}
     */
    public static Map<Integer, Match> assignSlots(List<Match> knockoutMatches) {
        Map<Stage, List<Match>> byStage = new EnumMap<>(Stage.class);
        for (Match m : knockoutMatches) {
            if (m.stage() == Stage.GROUP_STAGE) {
                continue;
            }
            byStage.computeIfAbsent(m.stage(), s -> new java.util.ArrayList<>()).add(m);
        }

        Map<Integer, Match> byFifaNo = new HashMap<>();
        for (Map.Entry<Stage, List<Match>> e : byStage.entrySet()) {
            List<Match> sorted = e.getValue().stream().sorted(BY_KICKOFF_THEN_ID).toList();
            for (int i = 0; i < sorted.size(); i++) {
                int slot = i + 1;
                int fifaNo = BracketTopology.fifaMatchNo(e.getKey(), slot);
                byFifaNo.put(fifaNo, sorted.get(i));
            }
        }
        return byFifaNo;
    }
}
