package com.example.wmtippspiel.reminder;

import java.util.List;

import com.example.wmtippspiel.domain.model.Match;

/**
 * Verschickt die Tipp-Erinnerung an die genannten Nutzer. Interface, damit die
 * Reminder-Logik ohne Discord testbar bleibt.
 */
public interface ReminderPublisher {

    void publishReminder(Match match, List<String> userIds);
}
