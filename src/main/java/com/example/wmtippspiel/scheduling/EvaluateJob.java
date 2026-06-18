package com.example.wmtippspiel.scheduling;

import com.example.wmtippspiel.evaluation.EvaluationService;
import com.example.wmtippspiel.leaderboard.LeaderboardBoardService;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Minütlicher Job zur Auswertung beendeter Spiele (F5). Nach einem Batch mit
 * mindestens einer Auswertung wird zusätzlich das Leaderboard-Board aktualisiert
 * (F11). Scoring selbst bleibt ausschließlich in {@link EvaluationService}.
 */
@Component
public class EvaluateJob {

    private final EvaluationService evaluationService;
    private final LeaderboardBoardService leaderboardBoardService;

    public EvaluateJob(EvaluationService evaluationService,
                       LeaderboardBoardService leaderboardBoardService) {
        this.evaluationService = evaluationService;
        this.leaderboardBoardService = leaderboardBoardService;
    }

    @Scheduled(fixedDelayString = "${app.jobs.evaluate-interval-ms:60000}", initialDelay = 20_000)
    public void evaluateFinishedMatches() {
        int evaluated = evaluationService.evaluateFinishedMatches();
        if (evaluated > 0) {
            leaderboardBoardService.refreshAfterEvaluation();
        }
    }
}
