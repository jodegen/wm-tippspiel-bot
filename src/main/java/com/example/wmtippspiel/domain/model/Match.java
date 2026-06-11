package com.example.wmtippspiel.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Eine WM-Begegnung. Zeitangaben sind UTC ({@link Instant}, Verfassung
 * Prinzip IV). Die abgeleiteten Prädikate kapseln die testbare Kernlogik für
 * Tippbarkeit, Reveal-Timing und Auswertungsfähigkeit (Verfassung Prinzip III).
 */
public record Match(
        long id,
        String home,
        String away,
        Instant kickoff,
        Stage stage,
        String groupLabel,
        String channel,
        BigDecimal oddsHome,
        BigDecimal oddsDraw,
        BigDecimal oddsAway,
        Integer homeScore,
        Integer awayScore,
        MatchStatus status,
        boolean revealed,
        boolean evaluated) {

    private static final String TBD = "TBD";

    /** Kopie mit gesetztem TV-Sender (für das manuell gepflegte Mapping beim Sync). */
    public Match withChannel(String newChannel) {
        return new Match(id, home, away, kickoff, stage, groupLabel, newChannel,
                oddsHome, oddsDraw, oddsAway, homeScore, awayScore, status, revealed, evaluated);
    }

    /** Beide Teilnehmer stehen fest (keine "TBD"-Begegnung). */
    public boolean teamsKnown() {
        return home != null && away != null && !TBD.equalsIgnoreCase(home) && !TBD.equalsIgnoreCase(away);
    }

    /** Tippbar: in der Zukunft, Teams bekannt, nicht abgesagt (FR-007/009). */
    public boolean isTippable(Instant now) {
        return status != MatchStatus.CANCELLED && teamsKnown() && kickoff.isAfter(now);
    }

    /** Reveal-fähig: Anpfiff erreicht, noch nicht offengelegt, nicht abgesagt (FR-011/012/013). */
    public boolean isRevealEligible(Instant now) {
        return !revealed && status != MatchStatus.CANCELLED && !kickoff.isAfter(now);
    }

    /** Auswertungsfähig: beendet, Endstand vorhanden, noch nicht ausgewertet (FR-014/016). */
    public boolean isEvaluationEligible() {
        return status == MatchStatus.FINISHED && !evaluated && homeScore != null && awayScore != null;
    }
}
