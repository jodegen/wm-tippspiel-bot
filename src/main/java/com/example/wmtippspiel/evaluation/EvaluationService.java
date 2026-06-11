package com.example.wmtippspiel.evaluation;

import java.util.ArrayList;
import java.util.List;

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
 * Wertet beendete Spiele aus und vergibt Punkte nach dem 3/1/0-Schema
 * (FR-014/015/016/017). Idempotent über das {@code evaluated}-Flag; bei
 * korrigiertem Endstand erfolgt eine Neubewertung mit Korrektur-Hinweis
 * (FR-017a). Verfassungsmäßig test-pflichtige Kernlogik (Prinzip III).
 */
@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

    private final MatchRepository matches;
    private final TipRepository tips;
    private final ScoringService scoring;
    private final EvaluationPublisher publisher;

    public EvaluationService(MatchRepository matches,
                             TipRepository tips,
                             ScoringService scoring,
                             EvaluationPublisher publisher) {
        this.matches = matches;
        this.tips = tips;
        this.scoring = scoring;
        this.publisher = publisher;
    }

    /** Job-Pfad: alle eval-fähigen Spiele erstmalig auswerten. */
    @Transactional
    public int evaluateFinishedMatches() {
        int evaluated = 0;
        for (Match match : matches.findUnevaluatedFinished()) {
            if (!match.isEvaluationEligible()) {
                continue;
            }
            evaluateMatch(match, false);
            evaluated++;
        }
        return evaluated;
    }

    /** Neubewertung nach Endstand-Korrektur (FR-017a); kennzeichnet den Post als Korrektur. */
    @Transactional
    public void reevaluate(Match match) {
        if (match.homeScore() == null || match.awayScore() == null) {
            return;
        }
        evaluateMatch(match, true);
    }

    private void evaluateMatch(Match match, boolean correction) {
        int homeActual = match.homeScore();
        int awayActual = match.awayScore();
        List<ScoredTip> scored = new ArrayList<>();
        for (Tip tip : tips.findByMatch(match.id())) {
            int pts = scoring.points(homeActual, awayActual, tip.homeScore(), tip.awayScore());
            tips.updatePoints(tip.userId(), match.id(), pts);
            scored.add(new ScoredTip(tip.username(), tip.homeScore(), tip.awayScore(), pts));
        }
        matches.markEvaluated(match.id());
        publisher.publishEvaluation(match, scored, correction);
        log.info("Spiel {} {}: {} Tipps gewertet",
                match.id(), correction ? "neu bewertet" : "ausgewertet", scored.size());
    }
}
