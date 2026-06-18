package com.example.wmtippspiel.domain.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Verfassung Prinzip III (NON-NEGOTIABLE): Pflicht-Tests der Punktewertung
 * nach dem CHECK24-Schema (FR-001/002/003). Stufen werden spezifisch → allgemein
 * geprüft (erste zutreffende gewinnt): 4 = exakt, 3 = richtige (vorzeichenbehaftete)
 * Tordifferenz, 2 = richtige Tendenz, 0 = daneben.
 *
 * <p>Testmatrix gemäß {@code contracts/scoring.md}.
 */
class ScoringServiceTest {

    private final ScoringService scoring = new ScoringService();

    @ParameterizedTest(name = "Ergebnis {0}:{1}, Tipp {2}:{3} → {4} Punkte")
    @CsvSource({
            // exaktes Ergebnis → 4
            "2, 1, 2, 1, 4",
            "0, 0, 0, 0, 4",
            "3, 3, 3, 3, 4",
            // richtige vorzeichenbehaftete Tordifferenz, nicht exakt → 3
            "4, 1, 3, 0, 3",   // Differenz +3
            "1, 1, 2, 2, 3",   // Remis, falsche Höhe (Differenz 0)
            "3, 3, 0, 0, 3",   // Remis, falsche Höhe (Differenz 0)
            "1, 2, 2, 3, 3",   // Differenz -1, Auswärtssieg
            // richtige Tendenz, weder exakt noch gleiche Differenz → 2
            "2, 0, 1, 0, 2",   // Heimsieg, Differenz ≠
            "3, 1, 1, 0, 2",   // Heimsieg, Differenz ≠
            "1, 2, 0, 3, 2",   // Auswärtssieg, Differenz ≠
            "4, 0, 2, 1, 2",   // Heimsieg, Differenz ≠ (+4 vs +1)
            // falsche Tendenz → 0
            "2, 0, 0, 2, 0",   // gespiegelte Differenz ⇒ falsche Tendenz
            "2, 2, 1, 2, 0",   // Remis vs. Auswärtssieg
            "1, 0, 0, 1, 0"    // falsche Tendenz
    })
    @DisplayName("CHECK24-Punktewertung 4/3/2/0 deckt exakt, Differenz, Tendenz und daneben ab")
    void awardsPointsByScheme(int homeActual, int awayActual, int homeTip, int awayTip, int expected) {
        assertThat(scoring.points(homeActual, awayActual, homeTip, awayTip)).isEqualTo(expected);
    }
}
