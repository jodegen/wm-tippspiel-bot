package com.example.wmtippspiel.presence;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.persistence.MatchRepository;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.SessionRecreateEvent;
import net.dv8tion.jda.api.events.session.SessionResumeEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Orchestriert die zustandsgesteuerte Bot-Presence (F9). Hält den zuletzt
 * gesetzten Anzeigetext, berechnet bei jedem {@link #recompute()} den
 * höchstpriorisierten Zustand über den {@link PresenceStateResolver} und setzt
 * die JDA-Activity (Typ {@code watching}) nur bei tatsächlicher Änderung
 * (FR-008), gedrosselt durch die {@link PresenceThrottle} (FR-009).
 *
 * <p>Trigger (rein additiv, kein eigener Scheduler, FR-004a): der bestehende
 * {@code liveGoalPoll}- und {@code boardRefresh}-Job rufen {@link #recompute()};
 * Initial-/Reconnect-Setzen erfolgt ereignisgetrieben über JDA-Session-Events
 * (FR-011, Verfassung Prinzip V). Die Selbst-Registrierung als Listener in
 * {@link #init()} vermeidet einen Build-Zeit-Zyklus mit dem {@code JDA}-Bean.
 */
@Component
public class PresenceManager extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(PresenceManager.class);

    private final JDA jda;
    private final MatchRepository matches;
    private final PresenceStateResolver resolver;
    private final PresenceThrottle throttle;
    private final Clock clock;
    private final String idleText;

    /** Beobachtete Live-Stände je Spiel (FR-013); nur prozesslokal (data-model.md). */
    private final Map<Long, Observed> observed = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final ScheduledExecutorService flushScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "presence-flush");
                t.setDaemon(true);
                return t;
            });
    private boolean flushScheduled;

    public PresenceManager(JDA jda,
                           MatchRepository matches,
                           PresenceStateResolver resolver,
                           Clock clock,
                           @Value("${app.presence.min-update-interval-ms:5000}") long minIntervalMs,
                           @Value("${app.presence.idle-text:🏆 WM 2026 /tipp}") String idleText) {
        this.jda = jda;
        this.matches = matches;
        this.resolver = resolver;
        this.clock = clock;
        this.throttle = new PresenceThrottle(Duration.ofMillis(minIntervalMs));
        this.idleText = idleText;
    }

    @PostConstruct
    void init() {
        jda.addEventListener(this);
        // Falls die Gateway-Verbindung bereits steht, initial sofort setzen
        // (sonst übernimmt onReady).
        if (jda.getStatus() == JDA.Status.CONNECTED) {
            recompute();
        }
    }

    @PreDestroy
    void shutdown() {
        flushScheduler.shutdownNow();
    }

    // --- JDA-Session-Events: Initial-/Reconnect-Setzen (FR-011) ---

    @Override
    public void onReady(ReadyEvent event) {
        recompute();
    }

    @Override
    public void onSessionResume(SessionResumeEvent event) {
        recompute();
    }

    @Override
    public void onSessionRecreate(SessionRecreateEvent event) {
        recompute();
    }

    /**
     * Bewertet den Presence-Zustand neu und setzt ihn (gedrosselt) nur bei
     * Textänderung. Thread-safe; aus jedem Trigger aufrufbar.
     */
    public void recompute() {
        lock.lock();
        try {
            Instant now = clock.instant();
            List<Match> inPlay = matches.findInPlay();
            List<LiveMatchView> views = updateObserved(inPlay, now);

            Match next = null;
            if (views.isEmpty()) {
                List<Match> upcoming = matches.findUpcoming(now, 1);
                next = upcoming.isEmpty() ? null : upcoming.get(0);
            }

            PresenceState state = resolver.resolve(views, next, idleText);
            apply(now, state.text());
        } catch (Exception e) {
            log.warn("Presence-Neuberechnung fehlgeschlagen", e);
        } finally {
            lock.unlock();
        }
    }

    /** Aktualisiert die Live-Stand-Beobachtung und liefert die Sichten für den Resolver. */
    private List<LiveMatchView> updateObserved(List<Match> inPlay, Instant now) {
        List<LiveMatchView> views = new ArrayList<>(inPlay.size());
        Map<Long, Observed> next = new HashMap<>(inPlay.size() * 2);
        for (Match m : inPlay) {
            int h = m.homeScore() == null ? 0 : m.homeScore();
            int a = m.awayScore() == null ? 0 : m.awayScore();
            Observed prev = observed.get(m.id());
            Instant lastChange;
            if (prev == null) {
                lastChange = null;                 // neu live, noch kein beobachtetes Tor (FR-013)
            } else if (prev.home != h || prev.away != a) {
                lastChange = now;                  // Stand geändert ⇒ jüngstes Ereignis
            } else {
                lastChange = prev.lastChange;
            }
            next.put(m.id(), new Observed(h, a, lastChange));
            views.add(new LiveMatchView(m.id(), m.home(), m.away(), h, a, m.kickoff(), lastChange));
        }
        observed.clear();
        observed.putAll(next);                     // beendete Spiele fallen heraus (LIVE-Austritt)
        return views;
    }

    private void apply(Instant now, String text) {
        Optional<String> sendNow = throttle.submit(text, now);
        if (sendNow.isPresent()) {
            setActivity(sendNow.get());
        } else {
            maybeScheduleFlush(now);
        }
    }

    /** Plant einen einzelnen verzögerten Flush, falls ein Wunsch ansteht (Coalescing). */
    private void maybeScheduleFlush(Instant now) {
        if (flushScheduled || !throttle.hasPending()) {
            return;
        }
        long delay = throttle.pendingDelayMillis(now);
        if (delay < 0) {
            return;
        }
        flushScheduled = true;
        log.debug("Presence-Update gedrosselt – verzögerter Flush in {} ms", delay);
        flushScheduler.schedule(this::runFlush, delay, TimeUnit.MILLISECONDS);
    }

    private void runFlush() {
        lock.lock();
        try {
            flushScheduled = false;
            Instant now = clock.instant();
            throttle.flush(now).ifPresent(this::setActivity);
            maybeScheduleFlush(now);               // erneut anstehender Wunsch?
        } catch (Exception e) {
            log.warn("Verzögerter Presence-Flush fehlgeschlagen", e);
        } finally {
            lock.unlock();
        }
    }

    private void setActivity(String text) {
        try {
            jda.getPresence().setActivity(Activity.watching(text));
            log.info("Presence aktualisiert: \"{}\"", text);
        } catch (Exception e) {
            log.warn("Presence konnte nicht gesetzt werden: {}", text, e);
        }
    }

    /** Prozesslokaler Schnappschuss eines beobachteten Live-Standes. */
    private record Observed(int home, int away, Instant lastChange) {
    }
}
