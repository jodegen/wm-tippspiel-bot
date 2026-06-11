package com.example.wmtippspiel.reminder;

import java.util.List;

/**
 * Liefert die Discord-User-IDs der WM-Notify-Empfänger. Interface, damit die
 * Reminder-Logik ohne JDA testbar bleibt.
 */
public interface NotifyAudience {

    List<String> roleMemberUserIds();
}
