package com.example.wmtippspiel.scheduling;

import com.example.wmtippspiel.live.GoalEvent;
import com.example.wmtippspiel.live.GoalEventSource;
import com.example.wmtippspiel.live.GoalNotifier;
import com.example.wmtippspiel.presence.PresenceManager;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Live-Tor-Polling (F8): fragt im Live-Fenster (Default alle 60 s) die
 * Event-Quelle ab und postet jedes Ereignis. Außerhalb des Fensters liefert die
 * Quelle keine Events (Filter dort), sodass kein Posting entsteht.
 *
 * <p>F9: Nach jedem Zyklus wird die Bot-Presence neu bewertet (LIVE-Eintritt/
 * -Stand/-Austritt), ohne eigenen Timer (FR-004a).
 */
@Component
public class LiveGoalPollJob {

    private final GoalEventSource goalEventSource;
    private final GoalNotifier goalNotifier;
    private final PresenceManager presenceManager;

    public LiveGoalPollJob(GoalEventSource goalEventSource, GoalNotifier goalNotifier,
                           PresenceManager presenceManager) {
        this.goalEventSource = goalEventSource;
        this.goalNotifier = goalNotifier;
        this.presenceManager = presenceManager;
    }

    @Scheduled(fixedDelayString = "${app.jobs.live-goal-poll-interval-ms:30000}", initialDelay = 40_000)
    public void postLiveGoals() {
        for (GoalEvent event : goalEventSource.fetchEvents()) {
            goalNotifier.post(event);
        }
        presenceManager.recompute();
    }
}
