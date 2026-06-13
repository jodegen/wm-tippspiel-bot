package com.example.wmtippspiel.live;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.MatchStatus;
import com.example.wmtippspiel.persistence.MatchRepository;
import com.example.wmtippspiel.persistence.MatchRepository.NotifiedScore;
import com.example.wmtippspiel.sync.FootballDataClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default-Quelle (F8): holt frische Stände über den bestehenden
 * {@link FootballDataClient} und lässt den {@link GoalDetector} die Differenz
 * gegen den persistierten gemeldeten Stand auflösen. Nur Spiele im Live-Fenster
 * (`kickoff` … `kickoff + 2,5 h`, Status SCHEDULED/IN_PLAY) werden betrachtet.
 */
@Component
public class ScoreDiffGoalEventSource implements GoalEventSource {

    private static final Logger log = LoggerFactory.getLogger(ScoreDiffGoalEventSource.class);
    private static final Duration WINDOW = Duration.ofMinutes(150);

    private final FootballDataClient client;
    private final MatchRepository matches;
    private final GoalDetector detector;
    private final Clock clock;

    public ScoreDiffGoalEventSource(FootballDataClient client,
                                    MatchRepository matches,
                                    GoalDetector detector,
                                    Clock clock) {
        this.client = client;
        this.matches = matches;
        this.detector = detector;
        this.clock = clock;
    }

    @Override
    public List<GoalEvent> fetchEvents() {
        Instant now = clock.instant();
        List<GoalEvent> events = new ArrayList<>();
        for (Match fresh : client.fetchMatches()) {
            // F9: frischen Stand+Status in matches halten, BEVOR die Goal-Guard
            // greift — erfasst auch den IN_PLAY→FINISHED-Übergang im Live-Takt,
            // sodass die Presence Eintritt/Austritt zeitnah aus der DB lesen kann.
            if (inTimeWindow(fresh, now) && fresh.homeScore() != null && fresh.awayScore() != null) {
                matches.updateLiveScore(fresh.id(), fresh.homeScore(), fresh.awayScore(), fresh.status());
            }
            if (!inLiveWindow(fresh, now) || fresh.homeScore() == null || fresh.awayScore() == null) {
                continue;
            }
            Optional<NotifiedScore> notified = matches.getNotifiedScore(fresh.id());
            if (notified.isEmpty()) {
                continue; // Spiel (noch) nicht in der DB – ohne Basis kein verlässliches Tracking
            }
            List<GoalEvent> matchEvents = detector.detect(
                    notified.get().home(), notified.get().away(),
                    fresh.homeScore(), fresh.awayScore(), fresh);
            if (!matchEvents.isEmpty()) {
                events.addAll(matchEvents);
                matches.updateNotifiedScore(fresh.id(), fresh.homeScore(), fresh.awayScore());
            }
        }
        if (!events.isEmpty()) {
            log.info("Live-Poll: {} Tor-/Korrektur-Ereignis(se)", events.size());
        }
        return events;
    }

    /** Live-Fenster: kickoff <= now <= kickoff + 2,5 h und Status SCHEDULED/IN_PLAY. */
    private boolean inLiveWindow(Match match, Instant now) {
        if (match.status() != MatchStatus.SCHEDULED && match.status() != MatchStatus.IN_PLAY) {
            return false;
        }
        return inTimeWindow(match, now);
    }

    /** Zeitfenster kickoff <= now <= kickoff + 2,5 h (statusunabhängig; für F9-Persistenz). */
    private boolean inTimeWindow(Match match, Instant now) {
        Instant kickoff = match.kickoff();
        return !kickoff.isAfter(now) && !now.isAfter(kickoff.plus(WINDOW));
    }
}
