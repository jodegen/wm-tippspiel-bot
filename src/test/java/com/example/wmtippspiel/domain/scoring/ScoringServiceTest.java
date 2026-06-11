package com.example.wmtippspiel.domain.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Verfassung Prinzip III (NON-NEGOTIABLE): Pflicht-Tests der Punktewertung
 * (FR-014). 3 = exakt, 1 = richtige Tendenz, 0 = daneben.
 */
class ScoringServiceTest {

    private final ScoringService scoring = new ScoringService();

    @ParameterizedTest(name = "Ergebnis {0}:{1}, Tipp {2}:{3} → {4} Punkte")
    @CsvSource({
            // exaktes Ergebnis → 3
            "2, 1, 2, 1, 3",
            "0, 0, 0, 0, 3",
            "3, 3, 3, 3, 3",
            // richtige Tendenz Heimsieg, falsches Ergebnis → 1
            "2, 1, 3, 0, 1",
            "2, 1, 1, 0, 1",
            // richtige Tendenz Auswärtssieg, falsches Ergebnis → 1
            "0, 2, 1, 3, 1",
            // richtige Tendenz Unentschieden, falsches Ergebnis → 1
            "2, 2, 0, 0, 1",
            "1, 1, 3, 3, 1",
            // falsche Tendenz → 0
            "2, 1, 1, 2, 0",
            "0, 0, 1, 0, 0",
            "2, 2, 2, 1, 0",
            "1, 0, 0, 0, 0"
    })
    @DisplayName("3/1/0-Punktewertung deckt exakt, Tendenz und daneben ab")
    void awardsPointsByScheme(int homeActual, int awayActual, int homeTip, int awayTip, int expected) {
        assertThat(scoring.points(homeActual, awayActual, homeTip, awayTip)).isEqualTo(expected);
    }
}
