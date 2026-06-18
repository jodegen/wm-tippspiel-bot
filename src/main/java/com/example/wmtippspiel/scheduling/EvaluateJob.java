package com.example.wmtippspiel.scheduling;

import com.example.wmtippspiel.evaluation.EvaluationService;
import com.example.wmtippspiel.leaderboard.LeaderboardBoardService;
import com.example.wmtippspiel.recap.MatchdayRecapService;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Minütlicher Job zur Auswertung beendeter Spiele (F5). Nach einem Batch mit
 * mindestens einer Auswertung werden zusätzlich das Leaderboard-Board (F11)
 * aktualisiert und abgeschlossene Spieltage als Rückblick (F12) gepostet. Scoring
 * selbst bleibt ausschließlich in {@link EvaluationService}.
 */
@Component
public class EvaluateJob {

    private final EvaluationService evaluationService;
    private final LeaderboardBoardService leaderboardBoardService;
    private final MatchdayRecapService matchdayRecapService;

    public EvaluateJob(EvaluationService evaluationService,
                       LeaderboardBoardService leaderboardBoardService,
                       MatchdayRecapService matchdayRecapService) {
        this.evaluationService = evaluationService;
        this.leaderboardBoardService = leaderboardBoardService;
        this.matchdayRecapService = matchdayRecapService;
    }

    @Scheduled(fixedDelayString = "${app.jobs.evaluate-interval-ms:60000}", initialDelay = 20_000)
    public void evaluateFinishedMatches() {
        int evaluated = evaluationService.evaluateFinishedMatches();
        if (evaluated > 0) {
            leaderboardBoardService.refreshAfterEvaluation();
            matchdayRecapService.postCompletedRecaps();
        }
    }
}
