package com.example.wmtippspiel.domain.scoring;

import org.springframework.stereotype.Service;

/**
 * Punktewertung nach dem 3/1/0-Schema (FR-014). Reine, von Discord und DB
 * entkoppelte Logik — verfassungsmäßig test-pflichtige Kernlogik (Prinzip III).
 *
 * <ul>
 *   <li><b>3</b> – exaktes Ergebnis</li>
 *   <li><b>1</b> – richtige Tendenz (Heimsieg/Auswärtssieg/Unentschieden), falsches Ergebnis</li>
 *   <li><b>0</b> – falsche Tendenz</li>
 * </ul>
 */
@Service
public class ScoringService {

    public int points(int homeActual, int awayActual, int homeTip, int awayTip) {
        if (homeActual == homeTip && awayActual == awayTip) {
            return 3;
        }
        if (tendency(homeActual, awayActual) == tendency(homeTip, awayTip)) {
            return 1;
        }
        return 0;
    }

    /** -1 = Auswärtssieg, 0 = Unentschieden, +1 = Heimsieg. */
    private int tendency(int home, int away) {
        return Integer.compare(home, away);
    }
}
