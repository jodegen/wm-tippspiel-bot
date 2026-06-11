package com.example.wmtippspiel.scheduling;

import com.example.wmtippspiel.kickoff.KickoffNoticeService;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Prüft minütlich, ob "Anpfiff steht bevor"-Hinweise fällig sind. */
@Component
public class KickoffNoticeJob {

    private final KickoffNoticeService kickoffNoticeService;

    public KickoffNoticeJob(KickoffNoticeService kickoffNoticeService) {
        this.kickoffNoticeService = kickoffNoticeService;
    }

    @Scheduled(fixedDelayString = "${app.jobs.kickoff-notice-interval-ms:60000}", initialDelay = 50_000)
    public void postKickoffNotices() {
        kickoffNoticeService.notifyUpcomingKickoffs();
    }
}
