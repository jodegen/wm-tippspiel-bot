package com.example.wmtippspiel.domain.scoring;

import org.springframework.stereotype.Service;

/**
 * Punktewertung nach dem CHECK24-Schema mit vierstufiger Staffelung
 * (FR-001/002/003). Reine, von Discord und DB entkoppelte Logik — die einzige
 * Stelle, an der Punkte berechnet werden (FR-011), genutzt von Auto-Auswertung
 * und rückwirkender Neuberechnung. Verfassungsmäßig test-pflichtige Kernlogik
 * (Prinzip III).
 *
 * <p>Die Stufen werden spezifisch → allgemein geprüft; die erste zutreffende
 * gewinnt:
 * <ul>
 *   <li><b>4</b> – exaktes Ergebnis</li>
 *   <li><b>3</b> – richtige vorzeichenbehaftete Tordifferenz, aber nicht exakt
 *       (schließt Unentschieden mit falscher Höhe ein, z. B. Tipp 2:2 / Ergebnis 1:1)</li>
 *   <li><b>2</b> – richtige Tendenz (Heimsieg/Auswärtssieg/Unentschieden), aber
 *       weder exakt noch gleiche Differenz</li>
 *   <li><b>0</b> – falsche Tendenz</li>
 * </ul>
 * Alle Spiele werden gleich gewertet, ohne Phasen-Gewichtung (FR-004).
 */
@Service
public class ScoringService {

    public int points(int homeActual, int awayActual, int homeTip, int awayTip) {
        if (homeActual == homeTip && awayActual == awayTip) {
            return 4;
        }
        if (homeActual - awayActual == homeTip - awayTip) {
            return 3;
        }
        if (tendency(homeActual, awayActual) == tendency(homeTip, awayTip)) {
            return 2;
        }
        return 0;
    }

    /** -1 = Auswärtssieg, 0 = Unentschieden, +1 = Heimsieg. */
    private int tendency(int home, int away) {
        return Integer.compare(home, away);
    }
}
