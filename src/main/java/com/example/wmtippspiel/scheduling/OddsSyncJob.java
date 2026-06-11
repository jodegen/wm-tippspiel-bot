package com.example.wmtippspiel.scheduling;

import com.example.wmtippspiel.sync.OddsSyncService;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodischer Quoten-Sync (~6 h; schont das Odds-API-Kontingent, research.md R5). */
@Component
public class OddsSyncJob {

    private final OddsSyncService oddsSyncService;

    public OddsSyncJob(OddsSyncService oddsSyncService) {
        this.oddsSyncService = oddsSyncService;
    }

    @Scheduled(fixedDelayString = "${app.jobs.odds-interval-ms:21600000}", initialDelay = 45_000)
    public void syncOdds() {
        oddsSyncService.sync();
    }
}
