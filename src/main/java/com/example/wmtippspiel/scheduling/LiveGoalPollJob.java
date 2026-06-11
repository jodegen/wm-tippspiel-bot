package com.example.wmtippspiel.scheduling;

import com.example.wmtippspiel.live.GoalEvent;
import com.example.wmtippspiel.live.GoalEventSource;
import com.example.wmtippspiel.live.GoalNotifier;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Live-Tor-Polling (F8): fragt im Live-Fenster (Default alle 60 s) die
 * Event-Quelle ab und postet jedes Ereignis. Außerhalb des Fensters liefert die
 * Quelle keine Events (Filter dort), sodass kein Posting entsteht.
 */
@Component
public class LiveGoalPollJob {

    private final GoalEventSource goalEventSource;
    private final GoalNotifier goalNotifier;

    public LiveGoalPollJob(GoalEventSource goalEventSource, GoalNotifier goalNotifier) {
        this.goalEventSource = goalEventSource;
        this.goalNotifier = goalNotifier;
    }

    @Scheduled(fixedDelayString = "${app.jobs.live-goal-poll-interval-ms:30000}", initialDelay = 40_000)
    public void postLiveGoals() {
        for (GoalEvent event : goalEventSource.fetchEvents()) {
            goalNotifier.post(event);
        }
    }
}
