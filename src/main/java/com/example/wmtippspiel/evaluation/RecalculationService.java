package com.example.wmtippspiel.evaluation;

import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.Tip;
import com.example.wmtippspiel.domain.scoring.ScoringService;
import com.example.wmtippspiel.persistence.MatchRepository;
import com.example.wmtippspiel.persistence.TipRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rückwirkende Neuberechnung aller bereits ausgewerteten Tipps nach dem aktuellen
 * Punkteschema (FR-008). Nutzt ausschließlich {@link ScoringService#points} als
 * einzige Berechnungsquelle (FR-011) und überschreibt {@code tips.points} nur bei
 * tatsächlicher Abweichung — dadurch ist der Lauf idempotent und gefahrlos
 * mehrfach ausführbar (FR-009). Spiele ohne {@code evaluated = true} werden nie
 * angefasst (FR-010). Vor jedem Überschreiben wird der alte → neue Wert geloggt
 * (Backup-Vorgabe).
 *
 * <p>Verfassungsmäßig test-pflichtige Kernlogik (Prinzip III).
 */
@Service
public class RecalculationService {

    private static final Logger log = LoggerFactory.getLogger(RecalculationService.class);

    private final MatchRepository matches;
    private final TipRepository tips;
    private final ScoringService scoring;

    public RecalculationService(MatchRepository matches, TipRepository tips, ScoringService scoring) {
        this.matches = matches;
        this.tips = tips;
        this.scoring = scoring;
    }

    /** Zusammenfassung eines Neuberechnungslaufs. */
    public record RecalcSummary(int matchesScanned, int tipsScanned, int tipsChanged) {
    }

    @Transactional
    public RecalcSummary recalculateAll() {
        int matchesScanned = 0;
        int tipsScanned = 0;
        int tipsChanged = 0;

        for (Match match : matches.findEvaluated()) {
            if (match.homeScore() == null || match.awayScore() == null) {
                continue; // ohne Endstand nicht bewertbar
            }
            matchesScanned++;
            int homeActual = match.homeScore();
            int awayActual = match.awayScore();
            for (Tip tip : tips.findByMatch(match.id())) {
                tipsScanned++;
                int recomputed = scoring.points(homeActual, awayActual, tip.homeScore(), tip.awayScore());
                if (recomputed != tip.points()) {
                    log.info("Neuberechnung: Spiel {} Tipp {} {}:{} — Punkte {} → {}",
                            match.id(), tip.userId(), tip.homeScore(), tip.awayScore(),
                            tip.points(), recomputed);
                    tips.updatePoints(tip.userId(), match.id(), recomputed);
                    tipsChanged++;
                }
            }
        }

        RecalcSummary summary = new RecalcSummary(matchesScanned, tipsScanned, tipsChanged);
        log.info("Rückwirkende Neuberechnung abgeschlossen: {} Spiele, {} Tipps geprüft, {} geändert",
                summary.matchesScanned(), summary.tipsScanned(), summary.tipsChanged());
        return summary;
    }
}
