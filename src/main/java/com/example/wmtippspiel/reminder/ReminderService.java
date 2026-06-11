package com.example.wmtippspiel.reminder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.Tip;
import com.example.wmtippspiel.persistence.MatchRepository;
import com.example.wmtippspiel.persistence.NotifySubscriberRepository;
import com.example.wmtippspiel.persistence.ReminderLogRepository;
import com.example.wmtippspiel.persistence.TipRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Erinnert WM-Notify-Abonnenten, die ein bald anstehendes Spiel noch nicht
 * getippt haben (Backlog-Feature E1). Pro Spiel genau einmal (Idempotenz über
 * {@link ReminderLogRepository}); zielt nur auf Abonnenten ohne Tipp.
 */
@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    private final MatchRepository matches;
    private final TipRepository tips;
    private final NotifySubscriberRepository subscribers;
    private final ReminderLogRepository reminderLog;
    private final ReminderPublisher publisher;
    private final Clock clock;
    private final long leadMinutes;

    public ReminderService(MatchRepository matches,
                           TipRepository tips,
                           NotifySubscriberRepository subscribers,
                           ReminderLogRepository reminderLog,
                           ReminderPublisher publisher,
                           Clock clock,
                           @Value("${app.reminder.lead-minutes:60}") long leadMinutes) {
        this.matches = matches;
        this.tips = tips;
        this.subscribers = subscribers;
        this.reminderLog = reminderLog;
        this.publisher = publisher;
        this.clock = clock;
        this.leadMinutes = leadMinutes;
    }

    /** Versendet fällige Erinnerungen und gibt die Anzahl benachrichtigter Spiele zurück. */
    public int remind() {
        Instant now = clock.instant();
        Instant until = now.plus(Duration.ofMinutes(leadMinutes));
        List<String> allSubscribers = subscribers.findAllUserIds();
        int reminded = 0;

        for (Match match : matches.findBetween(now, until)) {
            if (!match.isTippable(now) || reminderLog.wasReminded(match.id())) {
                continue;
            }
            Set<String> tipped = tips.findByMatch(match.id()).stream()
                    .map(Tip::userId)
                    .collect(Collectors.toSet());
            List<String> missing = allSubscribers.stream()
                    .filter(userId -> !tipped.contains(userId))
                    .toList();
            if (!missing.isEmpty()) {
                publisher.publishReminder(match, missing);
                reminded++;
                log.info("Tipp-Erinnerung für Spiel {} an {} Abonnent(en)", match.id(), missing.size());
            }
            reminderLog.markReminded(match.id(), now);
        }
        return reminded;
    }
}
