package com.example.wmtippspiel.scheduling;

import com.example.wmtippspiel.reveal.RevealService;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Minütlicher Job zur Offenlegung anstehender Spiele (F4). */
@Component
public class RevealJob {

    private final RevealService revealService;

    public RevealJob(RevealService revealService) {
        this.revealService = revealService;
    }

    @Scheduled(fixedDelayString = "${app.jobs.reveal-interval-ms:60000}", initialDelay = 15_000)
    public void revealDueMatches() {
        revealService.revealDueMatches();
    }
}
