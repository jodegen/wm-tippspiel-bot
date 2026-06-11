package com.example.wmtippspiel.scheduling;

import com.example.wmtippspiel.sync.MatchSyncService;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodischer Spielplan-/Ergebnis-Sync (~15 Min, schont das football-data.org
 * Free-Tier-Rate-Limit; research.md R5).
 */
@Component
public class SyncJob {

    private final MatchSyncService syncService;

    public SyncJob(MatchSyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(fixedDelayString = "${app.jobs.sync-interval-ms:900000}", initialDelay = 10_000)
    public void syncMatches() {
        syncService.sync();
    }
}
