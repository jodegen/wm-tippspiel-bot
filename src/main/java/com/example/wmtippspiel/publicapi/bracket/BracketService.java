package com.example.wmtippspiel.publicapi.bracket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.MatchStatus;
import com.example.wmtippspiel.domain.model.MatchWinner;
import com.example.wmtippspiel.domain.model.Stage;
import com.example.wmtippspiel.persistence.MatchRepository;
import com.example.wmtippspiel.publicapi.bracket.BracketTopology.Last32Placeholder;
import com.example.wmtippspiel.publicapi.bracket.BracketTopology.SourceRole;
import com.example.wmtippspiel.publicapi.bracket.BracketTopology.TopologyEntry;
import com.example.wmtippspiel.publicapi.dto.BracketDto;
import com.example.wmtippspiel.publicapi.dto.BracketMatchDto;
import com.example.wmtippspiel.publicapi.dto.BracketParticipantDto;
import com.example.wmtippspiel.publicapi.dto.BracketRoundDto;

import org.springframework.stereotype.Service;

/**
 * Baut den kompletten K.o.-Turnierbaum (Feature 010) aus der statischen
 * {@link BracketTopology} und den aktuellen {@code matches}-Daten zusammen:
 * <ul>
 *   <li>Struktur: alle 32 Spiele über sechs Runden mit FIFA-Nr und Kanten (US1).</li>
 *   <li>Platzhalter: offene Positionen tragen ein beschreibendes Label (US2).</li>
 *   <li>Gewinner-Fortschritt: Sieger rücken zur Laufzeit nach (nicht persistiert);
 *       Verlängerung/Elfmeter über den {@code winner} korrekt (US3).</li>
 * </ul>
 * Liefert ausschließlich unbedenkliche Felder (FR-014).
 */
@Service
public class BracketService {

    private static final String TBD = "TBD";

    private final MatchRepository matches;

    public BracketService(MatchRepository matches) {
        this.matches = matches;
    }

    /** Vollständiger Baum: immer alle sechs Runden in fester Reihenfolge (FR-002/003). */
    public BracketDto build() {
        Map<Integer, Match> byFifaNo = BracketSlotMapper.assignSlots(matches.findKnockout());

        List<BracketRoundDto> rounds = new ArrayList<>();
        for (Stage stage : BracketTopology.ROUND_ORDER) {
            List<BracketMatchDto> roundMatches = BracketTopology.ENTRIES.stream()
                    .filter(e -> e.stage() == stage)
                    .sorted(Comparator.comparingInt(TopologyEntry::slotIndex))
                    .map(e -> toMatchDto(e, byFifaNo))
                    .toList();
            rounds.add(new BracketRoundDto(
                    BracketTopology.apiStage(stage), BracketTopology.roundLabel(stage), roundMatches));
        }
        return new BracketDto(rounds);
    }

    private BracketMatchDto toMatchDto(TopologyEntry e, Map<Integer, Match> byFifaNo) {
        Match real = byFifaNo.get(e.fifaMatchNo());
        BracketParticipantDto home = resolveParticipant(e, byFifaNo, 0, real, true);
        BracketParticipantDto away = resolveParticipant(e, byFifaNo, 1, real, false);

        Long matchId = real == null ? null : real.id();
        Integer homeScore = real == null ? null : real.homeScore();
        Integer awayScore = real == null ? null : real.awayScore();
        String status = real == null ? null : real.status().name();
        MatchWinner side = winnerOf(real);
        String winner = side == null ? null : side.name();

        return new BracketMatchDto(e.fifaMatchNo(), matchId, home, away,
                homeScore, awayScore, status, winner, e.sourceMatchNos(), e.nextMatchNo());
    }

    /**
     * Beteiligter eines Slots: reales Team (falls bekannt), sonst statisches
     * LAST_32-Label, sonst Sieger/Verlierer des Quell-Spiels bzw. dessen
     * generischer Platzhalter. Genau eines von Team/Platzhalter ist gesetzt (SC-005).
     */
    private BracketParticipantDto resolveParticipant(TopologyEntry e, Map<Integer, Match> byFifaNo,
                                                     int slotIdx, Match real, boolean isHome) {
        if (real != null && real.teamsKnown()) {
            return BracketParticipantDto.team(isHome ? real.home() : real.away());
        }
        if (e.stage() == Stage.LAST_32) {
            Last32Placeholder ph = BracketTopology.LAST32_PLACEHOLDERS.get(e.fifaMatchNo());
            return BracketParticipantDto.placeholderOf(isHome ? ph.home() : ph.away());
        }
        int sourceNo = e.sourceMatchNos().get(slotIdx);
        String advancing = e.sourceRole() == SourceRole.WINNER
                ? winnerTeamName(sourceNo, byFifaNo)
                : loserTeamName(sourceNo, byFifaNo);
        if (advancing != null) {
            return BracketParticipantDto.team(advancing);
        }
        String verb = e.sourceRole() == SourceRole.WINNER ? "Sieger" : "Verlierer";
        return BracketParticipantDto.placeholderOf(verb + " Match " + sourceNo);
    }

    /**
     * Bestimmt die siegreiche Seite eines abgeschlossenen Spiels (US3, FR-010/011):
     * nicht FINISHED → {@code null}; gesetzter {@code winner} (HOME/AWAY) →
     * diese Seite (deckt Verlängerung/Elfmeter ab); sonst eindeutige Tordifferenz;
     * sonst {@code null} (Remis ohne Sieger-Info / DRAW → kein Nachrücken).
     */
    static MatchWinner winnerOf(Match m) {
        if (m == null || m.status() != MatchStatus.FINISHED) {
            return null;
        }
        if (m.winner() == MatchWinner.HOME_TEAM) {
            return MatchWinner.HOME_TEAM;
        }
        if (m.winner() == MatchWinner.AWAY_TEAM) {
            return MatchWinner.AWAY_TEAM;
        }
        if (m.homeScore() != null && m.awayScore() != null && !m.homeScore().equals(m.awayScore())) {
            return m.homeScore() > m.awayScore() ? MatchWinner.HOME_TEAM : MatchWinner.AWAY_TEAM;
        }
        return null;
    }

    private String winnerTeamName(int sourceNo, Map<Integer, Match> byFifaNo) {
        Match src = byFifaNo.get(sourceNo);
        MatchWinner side = winnerOf(src);
        if (side == null) {
            return null;
        }
        return realTeam(side == MatchWinner.HOME_TEAM ? src.home() : src.away());
    }

    private String loserTeamName(int sourceNo, Map<Integer, Match> byFifaNo) {
        Match src = byFifaNo.get(sourceNo);
        MatchWinner side = winnerOf(src);
        if (side == null) {
            return null;
        }
        return realTeam(side == MatchWinner.HOME_TEAM ? src.away() : src.home());
    }

    private static String realTeam(String name) {
        return (name != null && !TBD.equalsIgnoreCase(name)) ? name : null;
    }
}
