package com.example.wmtippspiel.recap;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.example.wmtippspiel.persistence.MatchdayTipRow;

/**
 * Reine (Discord-/DB-freie) Hilfslogik für den Spieltags-Rückblick (F12): Auswahl
 * des „besten Einzeltipps" und Anzeige-Label des Recap-Keys.
 */
public final class MatchdayRecaps {

    private MatchdayRecaps() {
    }

    /**
     * Bester Einzeltipp eines Spieltags: primär exakter Treffer (höchste Punkte),
     * fehlt ein solcher der höchstbepunktete Tipp. Tie-Break: das laut Quoten
     * <b>unwahrscheinlichste</b> tatsächliche Ergebnis (höchste Ergebnis-Quote);
     * fehlende Quoten zählen als 0. Restliche Gleichstände werden deterministisch
     * über Name/Begegnung aufgelöst (kein Zufall). Tipps mit 0 Punkten gelten nicht
     * als „bester Tipp".
     */
    public static Optional<MatchdayTipRow> bestTip(List<MatchdayTipRow> rows) {
        return rows.stream()
                .filter(r -> r.points() > 0)
                .max(Comparator
                        .comparingInt(MatchdayTipRow::points)
                        .thenComparing(MatchdayRecaps::resultOdds)
                        .thenComparing(MatchdayTipRow::username, Comparator.reverseOrder())
                        .thenComparing(MatchdayTipRow::home, Comparator.reverseOrder())
                        .thenComparing(MatchdayTipRow::away, Comparator.reverseOrder()));
    }

    /** Quote des tatsächlichen Ergebnisses (Heimsieg/Remis/Auswärtssieg); null ⇒ 0. */
    static BigDecimal resultOdds(MatchdayTipRow r) {
        if (r.resultHome() == null || r.resultAway() == null) {
            return BigDecimal.ZERO;
        }
        int cmp = Integer.compare(r.resultHome(), r.resultAway());
        BigDecimal odds = cmp > 0 ? r.oddsHome() : cmp < 0 ? r.oddsAway() : r.oddsDraw();
        return odds == null ? BigDecimal.ZERO : odds;
    }

    /** Anzeige-Label: {@code md:5} → „Spieltag 5"; {@code stage:LAST_16} → „Achtelfinale". */
    public static String label(String recapKey) {
        if (recapKey.startsWith("md:")) {
            return "Spieltag " + recapKey.substring(3);
        }
        if (recapKey.startsWith("stage:")) {
            return stageLabel(recapKey.substring("stage:".length()));
        }
        return recapKey;
    }

    private static String stageLabel(String stage) {
        return switch (stage) {
            case "GROUP_STAGE" -> "Gruppenphase";
            case "LAST_16" -> "Achtelfinale";
            case "QUARTER_FINAL" -> "Viertelfinale";
            case "SEMI_FINAL" -> "Halbfinale";
            case "THIRD_PLACE" -> "Spiel um Platz 3";
            case "FINAL" -> "Finale";
            default -> stage;
        };
    }
}
