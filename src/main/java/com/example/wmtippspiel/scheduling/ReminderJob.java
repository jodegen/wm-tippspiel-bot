package com.example.wmtippspiel.scheduling;

import com.example.wmtippspiel.reminder.ReminderService;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Prüft regelmäßig (~5 Min), ob Tipp-Erinnerungen fällig sind (E1). */
@Component
public class ReminderJob {

    private final ReminderService reminderService;

    public ReminderJob(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Scheduled(fixedDelayString = "${app.jobs.reminder-interval-ms:300000}", initialDelay = 60_000)
    public void sendReminders() {
        reminderService.remind();
    }
}
