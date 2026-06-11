package com.example.wmtippspiel.scheduling;

import com.example.wmtippspiel.evaluation.EvaluationService;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Minütlicher Job zur Auswertung beendeter Spiele (F5). */
@Component
public class EvaluateJob {

    private final EvaluationService evaluationService;

    public EvaluateJob(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @Scheduled(fixedDelayString = "${app.jobs.evaluate-interval-ms:60000}", initialDelay = 20_000)
    public void evaluateFinishedMatches() {
        evaluationService.evaluateFinishedMatches();
    }
}
